package io.jenkins.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import io.jenkins.plugins.QiniuFileSystem.InvalidPathError;
import jenkins.util.VirtualFile;

@Restricted(NoExternalUse.class)
public final class QiniuFile extends VirtualFile {
    private static final Logger LOG = Logger.getLogger(QiniuFile.class.getName());

    private String objectName, normalizedObjectName;

    @Nonnull
    private QiniuFileSystem qiniuFileSystem;

    public QiniuFile(@Nonnull final QiniuFileSystem qiniuFileSystem, final String objectName) {
        this.qiniuFileSystem = qiniuFileSystem;
        this.objectName = objectName;
        this.normalizedObjectName = this.getNormalizedObjectName();
    }

    @Nonnull
    public Path getPath() {
        try {
            String objectName = "";
            if (this.normalizedObjectName != null) {
                objectName = this.normalizedObjectName.toString();
            }
            final String objectNamePrefix = this.qiniuFileSystem.getObjectNamePrefix();
            if (!objectNamePrefix.isEmpty()) {
                final Path path = QiniuFileSystem
                        .fromObjectNameToFileSystemPath(this.qiniuFileSystem.getObjectNamePrefix());
                return path.resolve(QiniuFileSystem.fromObjectNameToFileSystemPath(objectName));
            } else {
                return QiniuFileSystem.fromObjectNameToFileSystemPath(objectName);
            }
        } catch (InvalidPathError e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public String getName() {
        LOG.log(Level.INFO, "QiniuFile::{0}::getName()", this.normalizedObjectName);
        final Path path = getPath().getFileName();
        if (path != null) {
            return path.toString();
        } else {
            return "";
        }
    }

    @Nonnull
    @Override
    public URI toURI() {
        LOG.log(Level.INFO, "QiniuFile::{0}::toURI()", this.normalizedObjectName);
        try {
            return this.toExternalURL().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public URL toExternalURL() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::toExternalURL()", this.normalizedObjectName);
        final QiniuConfig config = this.qiniuFileSystem.getConfig();
        String scheme = "http";
        if (config.isUseHTTPs()) {
            scheme = "https";
        }

        String objectName = QiniuFileSystem.fromFileSystemPathToObjectName(this.getPath());
        if (!objectName.startsWith(QiniuFileSystem.SEPARATOR)) {
            objectName = QiniuFileSystem.SEPARATOR + objectName;
        }

        String downloadDomain = config.getDownloadDomain();
        if (downloadDomain.isEmpty()) {
            downloadDomain = config.getBucketManager().getDefaultIoSrcHost(config.getBucketName());
        }
        final String url = new URL(scheme, downloadDomain, objectName).toString();
        LOG.log(Level.INFO, "QiniuFile::{0}::toExternalURL() url={0}", url);
        return new URL(this.qiniuFileSystem.getConfig().getAuth().privateDownloadUrl(url, 180));
    }

    @CheckForNull
    @Override
    public VirtualFile getParent() {
        LOG.log(Level.INFO, "QiniuFile::{0}::getParent()", this.normalizedObjectName);
        try {
            if (this.normalizedObjectName != null) {
                final Path parentPath = QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName)
                        .getParent();
                return new QiniuFile(this.qiniuFileSystem, QiniuFileSystem.fromFileSystemPathToObjectName(parentPath));
            } else {
                return null;
            }
        } catch (InvalidPathError e) {
            return null;
        }
    }

    @Override
    public boolean isDirectory() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::isDirectory()", this.normalizedObjectName);
        this.qiniuFileSystem.mayThrowIOException();
        if (this.normalizedObjectName != null) {
            final QiniuFileSystem.Node node = this.qiniuFileSystem
                    .getNodeByPath(QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName), false,
                            false);
            if (node != null) {
                return node.isDirectory();
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public boolean isFile() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::isFile()", this.normalizedObjectName);
        this.qiniuFileSystem.mayThrowIOException();
        if (this.normalizedObjectName != null) {
            final QiniuFileSystem.Node node = this.qiniuFileSystem
                    .getNodeByPath(QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName), false,
                            false);
            if (node != null) {
                return node.isFile();
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public boolean exists() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::exists()", this.normalizedObjectName);
        this.qiniuFileSystem.mayThrowIOException();
        if (this.normalizedObjectName != null) {
            this.qiniuFileSystem.getNodeByPath(
                    QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName), false,
                    false);
        }
        return true;
    }

    @Nonnull
    @Override
    public VirtualFile[] list() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::list()", this.normalizedObjectName);
        this.qiniuFileSystem.mayThrowIOException();
        QiniuFileSystem.DirectoryNode currentNode = this.qiniuFileSystem.getRootNode();
        if (this.normalizedObjectName != null) {
            currentNode = this.qiniuFileSystem
                    .getDirectoryNodeByPath(QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName),
                            false);
        }
        final Collection<QiniuFileSystem.Node> childrenNodes = currentNode.getChildrenNodes();
        VirtualFile[] virtualFiles = new VirtualFile[childrenNodes.size()];
        int i = 0;
        for (QiniuFileSystem.Node childNode : childrenNodes) {
            String objectName;
            if (this.normalizedObjectName != null) {
                objectName = QiniuFileSystem.fromFileSystemPathToObjectName(QiniuFileSystem
                        .fromObjectNameToFileSystemPath(this.normalizedObjectName).resolve(childNode.getNodeName()));
            } else {
                objectName = childNode.getNodeName();
            }
            virtualFiles[i] = new QiniuFile(this.qiniuFileSystem, objectName);
            i++;
        }
        return virtualFiles;
    }

    public boolean deleteRecursively() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::deleteRecursively()", this.normalizedObjectName);
        this.qiniuFileSystem.mayThrowIOException();
        if (this.qiniuFileSystem.getRootNode().getChildrenCount() == 0) {
            return false;
        }
        this.qiniuFileSystem.deleteAll();
        return true;
    }

    @Nonnull
    @Override
    public VirtualFile child(@Nonnull String childName) {
        LOG.log(Level.INFO, "QiniuFile::{0}::child({1})", new Object[] { this.normalizedObjectName, childName });
        String objectName;
        try {
            if (this.normalizedObjectName != null) {
                objectName = QiniuFileSystem.fromFileSystemPathToObjectName(
                        QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName).resolve(childName));
            } else {
                objectName = childName;
            }
            return new QiniuFile(this.qiniuFileSystem, objectName);
        } catch (InvalidPathError e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long length() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::length()", this.normalizedObjectName);
        QiniuFileSystem.Node currentNode = this.qiniuFileSystem.getRootNode();
        if (this.normalizedObjectName != null) {
            currentNode = this.qiniuFileSystem
                    .getNodeByPath(QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName), false,
                            false);
        }
        if (currentNode == null) {
            return 0;
        } else if (currentNode.isFile()) {
            return ((QiniuFileSystem.FileNode) currentNode).getMetadata().fsize;
        } else {
            return ((QiniuFileSystem.DirectoryNode) currentNode).getChildrenCount();
        }
    }

    @Override
    public long lastModified() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::lastModified()", this.normalizedObjectName);
        QiniuFileSystem.Node currentNode = this.qiniuFileSystem.getRootNode();
        if (this.normalizedObjectName != null) {
            currentNode = this.qiniuFileSystem
                    .getNodeByPath(QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName), false,
                            false);
        }
        if (currentNode != null && currentNode.isFile()) {
            return ((QiniuFileSystem.FileNode) currentNode).getMetadata().putTime / 10000;
        } else {
            return 0;
        }
    }

    @Override
    public boolean canRead() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::canRead()", this.normalizedObjectName);
        QiniuFileSystem.Node currentNode = this.qiniuFileSystem.getRootNode();
        if (this.normalizedObjectName != null) {
            currentNode = this.qiniuFileSystem
                    .getNodeByPath(QiniuFileSystem.fromObjectNameToFileSystemPath(this.normalizedObjectName), false,
                            false);
        }
        return currentNode != null;
    }

    @Nonnull
    @Override
    public InputStream open() throws IOException {
        LOG.log(Level.INFO, "QiniuFile::{0}::open()", this.normalizedObjectName);
        return this.toExternalURL().openStream();
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.writeUTF(this.objectName);
        out.writeUTF(this.normalizedObjectName);
        out.writeObject(this.qiniuFileSystem);
        LOG.log(Level.INFO, "QiniuFile::{0}::writeObject()", this.normalizedObjectName);
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.objectName = in.readUTF();
        this.normalizedObjectName = in.readUTF();
        this.qiniuFileSystem = (QiniuFileSystem) in.readObject();
        LOG.log(Level.INFO, "QiniuFile::{0}::readObject()", this.normalizedObjectName);
    }

    private String getNormalizedObjectName() {
        if (this.objectName != null) {
            final String VIEW_SUFFIX = "/*view*";
            if (this.objectName.endsWith(VIEW_SUFFIX)) {
                return this.objectName.substring(0, this.objectName.length() - VIEW_SUFFIX.length());
            } else {
                return this.objectName;
            }
        } else {
            return null;
        }
    }
}
