package io.jenkins.plugins.devsecops;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;

/** Copies the bundled workflow library resources into the Pipeline shared library workspace. */
class DevSecOpsLibraryRetriever extends LibraryRetriever {
    static final String RESOURCE_ROOT = "workflow-libs";

    @Override
    public void retrieve(
            @Nonnull LibraryRetrievalContext context,
            @Nonnull FilePath target,
            @Nonnull TaskListener listener)
            throws Exception {
        copyResources(RESOURCE_ROOT, target);
    }

    private void copyResources(String resourceRoot, FilePath target)
            throws IOException, URISyntaxException, InterruptedException {
        ClassLoader classLoader = DevSecOpsLibraryRetriever.class.getClassLoader();
        URL resourceUrl = classLoader.getResource(resourceRoot);
        if (resourceUrl == null) {
            throw new AbortException("Unable to locate bundled library resources under " + resourceRoot);
        }

        if ("file".equals(resourceUrl.getProtocol())) {
            Path source = Paths.get(resourceUrl.toURI());
            copyFromFileSystem(source, target, source);
            return;
        }

        if ("jar".equals(resourceUrl.getProtocol())) {
            try (FileSystem fs = FileSystems.newFileSystem(resourceUrl.toURI(), Collections.emptyMap())) {
                Path source = fs.getPath("/" + resourceRoot);
                copyFromFileSystem(source, target, source);
                return;
            }
        }

        throw new AbortException(
                "Unsupported resource protocol for bundled library: " + resourceUrl.getProtocol());
    }

    private void copyFromFileSystem(Path sourceRoot, FilePath target, Path walkingRoot)
            throws IOException, InterruptedException {
        try (var stream = Files.walk(walkingRoot)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> copyPath(path, sourceRoot, target));
        }
    }

    private void copyPath(Path path, Path sourceRoot, FilePath target) {
        Path relative = sourceRoot.relativize(path);
        try (InputStream in = Files.newInputStream(path)) {
            FilePath destination = target.child(relative.toString().replace('\\', '/'));
            FilePath parent = destination.getParent();
            if (parent != null) {
                parent.mkdirs();
            }
            destination.copyFrom(in);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to copy bundled library resource: " + relative, e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DevSecOpsLibraryRetriever;
    }

    @Override
    public int hashCode() {
        return DevSecOpsLibraryRetriever.class.hashCode();
    }
}
