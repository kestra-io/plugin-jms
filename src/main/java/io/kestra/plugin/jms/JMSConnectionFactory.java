package io.kestra.plugin.jms;

import at.conapi.plugins.common.endpoints.jms.adapter.JmsFactory;
import at.conapi.plugins.common.endpoints.jms.adapter.impl.ConnectionFactoryAdapter;
import io.kestra.plugin.jms.configuration.ConnectionFactoryConfig;
import io.kestra.core.runners.RunContext;
import io.kestra.core.plugins.PluginClassLoader;

import javax.naming.Context;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;

public class JMSConnectionFactory {

    public ConnectionFactoryAdapter create(RunContext runContext, ConnectionFactoryConfig config) throws Exception {

        // Step 1: Resolve the provider JAR paths to a list of URLs using the utility method.
        List<URL> jarUrls = resolveProviderJarUrls(runContext, config.getProviderJarPaths());

        // Step 2: Instantiate your common JmsFactory with the classloader
        // Pass the current plugin's classloader as parent to ensure proper JMS API delegation
        JmsFactory jmsFactory = new JmsFactory(jarUrls.toArray(new URL[0]), this.getClass().getClassLoader());

        // Step 3: Delegate the creation logic to the JmsFactory
        if (config instanceof ConnectionFactoryConfig.Direct directConfig) {
            return createDirectConnectionFactory(runContext, directConfig, jmsFactory);
        }
        if (config instanceof ConnectionFactoryConfig.Jndi jndiConfig) {
            return createJndiConnectionFactory(runContext, jndiConfig, jmsFactory);
        }

        throw new IllegalArgumentException("Unsupported ConnectionFactoryConfig type.");
    }

    /**
     * Utility method to convert a list of string paths into a list of usable URLs.
     *
     * @param runContext The Kestra RunContext for variable rendering and storage access.
     * @param providerJarPaths The list of JAR paths from the configuration.
     * @return A List of URL objects pointing to the local JAR files.
     * @throws Exception if rendering or file operations fail.
     */
    private List<URL> resolveProviderJarUrls(RunContext runContext, List<String> providerJarPaths) throws Exception {
        if (providerJarPaths == null || providerJarPaths.isEmpty()) {
            // if the user has not configured specific jar paths, then we try to load the defaults
            return getNestedJarUrls("jms-libs");
        }

        List<URL> jarUrls = new ArrayList<>();
        for (String path : providerJarPaths) {
            String renderedPath = runContext.render(path);
            URI uri = new URI(renderedPath);
            jarUrls.add(uri.toURL());
        }
        return jarUrls;
    }

    private ConnectionFactoryAdapter createDirectConnectionFactory(RunContext runContext, ConnectionFactoryConfig.Direct config, JmsFactory jmsFactory) throws Exception {
        Map<String, String> properties = new HashMap<>();
        if (config.getConnectionProperties() != null) {
            for(Map.Entry<String, String> entry : config.getConnectionProperties().entrySet()) {
                properties.put(entry.getKey(), runContext.render(entry.getValue()));
            }
        }
        return (ConnectionFactoryAdapter) jmsFactory.createConnectionFactory(config.getConnectionFactoryClass(), properties);
    }

    private ConnectionFactoryAdapter createJndiConnectionFactory(RunContext runContext, ConnectionFactoryConfig.Jndi config, JmsFactory jmsFactory) throws Exception {
        Hashtable<String, String> jndiProperties = new Hashtable<>();
        jndiProperties.put(Context.INITIAL_CONTEXT_FACTORY, config.getJndiInitialContextFactory());
        jndiProperties.put(Context.PROVIDER_URL, runContext.render(config.getJndiProviderUrl()));

        // credentials for the JNDI lookup can be set via plugin properties, or via the connection properties directly
        if(config.getJndiPrincipal() != null) {
            jndiProperties.put(Context.SECURITY_PRINCIPAL, config.getJndiPrincipal());
        }
        if(config.getJndiCredentials() != null) {
            jndiProperties.put(Context.SECURITY_CREDENTIALS, config.getJndiCredentials());
        }

        if (config.getConnectionProperties() != null) {
            for (Map.Entry<String, String> entry : config.getConnectionProperties().entrySet()) {
                jndiProperties.put(entry.getKey(), runContext.render(entry.getValue()));
            }
        }

        return (ConnectionFactoryAdapter) jmsFactory.lookupConnectionFactory(jndiProperties, config.getJndiConnectionFactoryName());
    }

    /**
     * Extracts JAR URLs from a subfolder in the same directory as the plugin JAR.
     * <p>
     * The plugin location is obtained from the PluginClassLoader and must be a valid URI.
     * If the plugin is at /app/plugins/plugin-jms-1.0.jar and subfolderPath is "jms-libs",
     * this method will look for JARs in /app/plugins/jms-libs/
     *
     * @param subfolderPath The name of the subfolder to look for (e.g., "jms-libs")
     * @return List of URLs pointing to JAR files within the specified subfolder, or empty list if folder doesn't exist
     * @throws IOException if there's an error reading the directory or converting URIs to URLs
     * @throws IllegalStateException if not called from within a Kestra plugin context
     * @throws IllegalArgumentException if the plugin location is not a valid URI
     */
    public List<URL> getNestedJarUrls(String subfolderPath) throws IOException {

        // Get the plugin class loader
        ClassLoader classLoader = this.getClass().getClassLoader();
        if (!(classLoader instanceof PluginClassLoader pluginClassLoader)) {
            throw new IllegalStateException("This method must be called from within a Kestra plugin context");
        }

        String pluginJarLocation = pluginClassLoader.location();

        // Find the main plugin JAR URL - use URI.create().toURL() instead of deprecated URL constructor
        URL pluginJarUrl;
        try {
            pluginJarUrl = URI.create(pluginJarLocation).toURL();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid plugin location URI: " + pluginJarLocation, e);
        }

        // Extract JARs from filesystem folder
        return new ArrayList<>(extractFromFilesystemFolder(pluginJarUrl, subfolderPath));
    }

    /**
     * Extracts JAR URLs from a filesystem folder in the same directory as the plugin JAR.
     * For example, if the plugin is at /app/plugins/plugin-jms-1.0.jar,
     * and subfolderPath is "jms-libs", it will look in /app/plugins/jms-libs/
     */
    private List<URL> extractFromFilesystemFolder(URL pluginUrl, String subfolderPath) throws IOException {
        List<URL> jarUrls = new ArrayList<>();

        // Get the plugin JAR file
        File pluginFile = new File(pluginUrl.getFile());

        // Get the parent directory where the plugin JAR is located
        File pluginDir = pluginFile.getParentFile();

        // Construct the path to the subfolder
        File libsFolder = new File(pluginDir, subfolderPath);

        // Check if the folder exists and is a directory
        if (!libsFolder.exists() || !libsFolder.isDirectory()) {
            // Return empty list if folder doesn't exist
            return jarUrls;
        }

        // List all JAR files in the subfolder
        File[] jarFiles = libsFolder.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                // Convert each JAR file to a URL
                jarUrls.add(jarFile.toURI().toURL());
            }
        }

        return jarUrls;
    }

}

