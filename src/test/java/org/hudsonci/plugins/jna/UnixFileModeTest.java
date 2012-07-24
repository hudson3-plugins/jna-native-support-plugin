/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hudsonci.plugins.jna;

import java.io.File;
import java.io.IOException;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;
import hudson.Functions;

/**
 * Unit Test for File modes of JNA based Native Unix Support
 * These tests can not run on a windows machine
 *
 * @author Winston Prakash
 */
public class UnixFileModeTest {

    /**
     * Test of mode method, of class JnaNativeUnixSupport.
     */
    @Test
    public void testMode() throws IOException {
        if (Functions.isWindows()) return;
        File file = File.createTempFile("test", "test");
        JnaNativeUnixSupport instance = new JnaNativeUnixSupport();
        int expResult = 33188; // Octal 100644
        int result = instance.mode(file);
        System.out.println("Mode of the file " + Integer.toOctalString(result));
        Assert.assertEquals(expResult, result);
    }

    /**
     * Test of chmod method, of class JnaNativeUnixSupport.
     */
    @Test
    public void testChmod() throws IOException {
        if (Functions.isWindows()) return;
        File file = File.createTempFile("test", "test");
        int mask = Integer.parseInt("100655", 8);
        JnaNativeUnixSupport instance = new JnaNativeUnixSupport();
        boolean result = instance.chmod(file, mask);
        Assert.assertTrue(result);
        int resultMask = instance.mode(file);
        System.out.println("New mode of the file " + Integer.toOctalString(resultMask));
        Assert.assertEquals(mask, resultMask);
    }
    
    /**
     * Test of chown method, of class JnaNativeUnixSupport.
     */
    @Test
    public void testChown() throws IOException {
        if (Functions.isWindows()) return;
        File file = File.createTempFile("test", "test");
        JnaNativeUnixSupport instance = new JnaNativeUnixSupport();
        int uid = instance.getEuid();
        int gid = instance.getEgid();
        boolean result = instance.chown(file, uid, gid);
        Assert.assertTrue(result);
        boolean result2 = instance.chown(file, uid + 1, gid);
        System.out.println("Chown: " + instance.getLastError());
        Assert.assertFalse(result2);
    }
    
     /**
     * Test of makeFileWritable method, of class JnaNativeUnixSupport.
     */
    @Test
    public void testMakeFileWritable() throws IOException {
        if (Functions.isWindows()) return;
        File file = File.createTempFile("test", "test");
        file.deleteOnExit();
        JnaNativeUnixSupport instance = new JnaNativeUnixSupport();
        // Change the mode to read only
        int mask = Integer.parseInt("100444", 8);
        instance.chmod(file, mask);
        System.out.println("Mode of the read only file " + Integer.toOctalString(instance.mode(file)));
        boolean result = instance.makeFileWritable(file);
        Assert.assertTrue(result);
        int expResult = 33188; // Octal 100644
        int resultMode = instance.mode(file);
        System.out.println("Mode of the writable file " + Integer.toOctalString(resultMode));
        Assert.assertEquals(expResult, resultMode);
    }
    
     /**
     * Test of createSymlink method, of class JnaNativeUnixSupport.
     */
    @Test
    public void testCreateSymlink() throws IOException {
        if (Functions.isWindows()) return;
        File symlinkToFile = File.createTempFile("test", "test");
        symlinkToFile.deleteOnExit();
        File tempDir = symlinkToFile.getParentFile();
        String targetPath = tempDir.getAbsolutePath() + "/" + RandomStringUtils.randomAlphanumeric(8);
        System.out.println(targetPath);
        JnaNativeUnixSupport instance = new JnaNativeUnixSupport();
        boolean result = instance.createSymlink(targetPath, symlinkToFile);
        if (!result) {
            System.out.println("Create Symlink: " + instance.getLastError());
        }
        Assert.assertTrue(result);
    }
    
     /**
     * Test of createSymlink method, of class JnaNativeUnixSupport.
     */
    @Test
    public void testCreateSymlinkUsingPosix() throws IOException {
        if (Functions.isWindows()) return;
        File symlinkToFile = File.createTempFile("test", "test");
        symlinkToFile.deleteOnExit();
        File tempDir = symlinkToFile.getParentFile();
        String targetPath = tempDir.getAbsolutePath() + "/" + RandomStringUtils.randomAlphanumeric(8);
        System.out.println(targetPath);
        JnaNativeUnixSupport instance = new JnaNativeUnixSupport();
        boolean result = instance.createSymlink(targetPath, symlinkToFile, true);
        if (!result) {
            System.out.println("Create Symlink: " + instance.getLastError());
        }
        Assert.assertTrue(result);
    }

    /**
     * Test of resolveSymlink method, of class JnaNativeUnixSupport.
     */
    @Test
    public void testResolveSymlink() throws IOException {
        if (Functions.isWindows()) return;
        File symlinkToFile = File.createTempFile("test", "test");
        symlinkToFile.deleteOnExit();
        File tempDir = symlinkToFile.getParentFile();
        String targetPath = tempDir.getAbsolutePath() + "/" + RandomStringUtils.randomAlphanumeric(8);
        System.out.println(targetPath);
        JnaNativeUnixSupport instance = new JnaNativeUnixSupport();
        instance.createSymlink(targetPath, symlinkToFile);
        File linkFile = new File(targetPath);
        String result = instance.resolveSymlink(linkFile);
        Assert.assertEquals(symlinkToFile.getAbsolutePath(), result);
    }
}
