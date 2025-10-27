package io.jenkins.plugins.devsecops;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryResolver;

/** Makes the bundled DevSecOps shared library available to Pipeline jobs. */
@Extension
public class DevSecOpsLibraryResolver extends LibraryResolver {
    public static final String LIBRARY_NAME = "devsecops";

    @Override
    public LibraryConfiguration forName(String name) {
        if (name != null && !LIBRARY_NAME.equals(name)) {
            return null;
        }
        LibraryConfiguration configuration =
                new LibraryConfiguration(LIBRARY_NAME, new DevSecOpsLibraryRetriever());
        configuration.setImplicit(true);
        configuration.setAllowVersionOverride(false);
        return configuration;
    }
}
