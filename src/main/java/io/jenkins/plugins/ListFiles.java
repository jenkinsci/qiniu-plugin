package io.jenkins.plugins;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.types.FileSet;

import hudson.Util;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

final class ListFiles extends MasterToSlaveFileCallable<Map<String, String>> {
    private static final long serialVersionUID = 1;

    @Nonnull
    private final String includes, excludes;
    private final boolean defaultExcludes;
    private final boolean caseSensitive;

    ListFiles(@Nonnull String includes, @Nonnull String excludes, boolean defaultExcludes, boolean caseSensitive) {
        this.includes = includes;
        this.excludes = excludes;
        this.defaultExcludes = defaultExcludes;
        this.caseSensitive = caseSensitive;
    }

    @Override
    public Map<String, String> invoke(File basedir, VirtualChannel channel)
            throws IOException, InterruptedException {
        final Map<String, String> r = new HashMap<>();

        final FileSet fileSet = Util.createFileSet(basedir, includes, excludes);
        fileSet.setDefaultexcludes(defaultExcludes);
        fileSet.setCaseSensitive(caseSensitive);

        for (String filePath : fileSet.getDirectoryScanner().getIncludedFiles()) {
            String objectName = filePath;
            if (QiniuFileSystem.SEPARATOR_CHAR != File.separatorChar) {
                objectName = String.join(QiniuFileSystem.SEPARATOR, StringUtils.split(
                        filePath, File.separatorChar));
            }
            r.put(filePath, objectName);
        }
        return r;
    }
}
