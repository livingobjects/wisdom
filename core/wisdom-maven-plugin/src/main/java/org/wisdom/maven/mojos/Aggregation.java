/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wisdom.maven.mojos;

import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Configure aggregations.
 */
public class Aggregation {

    private String output;

    private boolean minification = true;

    private List<String> files = new ArrayList<>();

    private static final FileSetManager FILESET_MANAGER = new FileSetManager();

    private List<FileSet> filesets;

    private boolean removeIncludedFiles;

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }

    public boolean isMinification() {
        return minification;
    }

    public void setMinification(boolean minification) {
        this.minification = minification;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    /**
     * @return the file sets.
     */
    public List<FileSet> getFilesets() {
        return filesets;
    }

    /**
     * Sets the file sets to include in the aggregation.
     *
     * @param fileset the file sets
     */
    public void setFilesets(List<FileSet> fileset) {
        this.filesets = fileset;
    }

    /**
     * @return the selected set of files.
     */
    public Collection<File> getSelectedFiles(File defaultBaseDirectory) {
        // Because of a symlink issue on OpenJDK, we cannot use the FileSetManager directory, because we need to
        // override a method from the DirectoryScanner.
        // The exception is: java.lang.ClassNotFoundException: sun/nio/fs/AixFileSystemProvider

        List<File> files = new ArrayList<>();
        final List<FileSet> sets = getFilesets();

        for (FileSet set : sets) {
            File base;
            if (set.getDirectory() == null) {
                // Set the directory if not set
                set.setDirectory(defaultBaseDirectory.getAbsolutePath());
                base = defaultBaseDirectory;
            } else {
                base = new File(set.getDirectory());
            }

            String[] names = FILESET_MANAGER.getIncludedFiles(set);

            for (String n : names) {
                files.add(new File(base, n));
            }
        }
        return files;
    }

    public boolean isRemoveIncludedFiles() {
        return removeIncludedFiles;
    }

    public void setRemoveIncludedFiles(boolean removeIncludedFiles) {
        this.removeIncludedFiles = removeIncludedFiles;
    }
}
