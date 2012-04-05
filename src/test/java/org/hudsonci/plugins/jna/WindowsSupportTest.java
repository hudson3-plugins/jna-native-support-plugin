/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hudsonci.plugins.jna;

import hudson.Functions;
import hudson.util.jna.NativeFunction;
import hudson.util.jna.NativeProcess;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.*;
import static org.junit.Assert.*;

/**
 * Unit Test for JNA based Native Window Support
 * These tests can run only a windows machine
 * 
 * @author Winston Prakash
 */
public class WindowsSupportTest {
    /**
     * Test of getLastError method, of class JnaNativeWindowsSupport.
     * Nothing so should return error code o
     */
    @Test
    public void testGetLastError() {
        if (!Functions.isWindows()) return;
        JnaNativeWindowsSupport instance = new JnaNativeWindowsSupport();
        String expResult = "Native Window Error Code: 0";
        String result = instance.getLastError();
        System.out.println(result);
        assertEquals(expResult, result);
    }

    /**
     * Test of isDotNetInstalled method, of class JnaNativeWindowsSupport.
     */
    @Test
    public void testIsDotNetInstalled() {
        if (!Functions.isWindows()) return;
        int major = 2;
        int minor = 0;
        JnaNativeWindowsSupport instance = new JnaNativeWindowsSupport();
        boolean result = instance.isDotNetInstalled(major, minor);
        assertTrue(result);
    }

    /**
     * Test of getWindowsProcesses method, of class JnaNativeWindowsSupport.
     * Nothing special but simply prints process info and verifies the method works
     */
    @Test
    public void testGetWindowsProcesses() {
        if (!Functions.isWindows()) return;
        JnaNativeWindowsSupport instance = new JnaNativeWindowsSupport();
        List<NativeProcess> processes = instance.getWindowsProcesses();
        for (NativeProcess process : processes){
            System.out.println("Process ID: " + process.getPid());
            System.out.println("Parent Process ID: " + process.getPpid());
            System.out.println("Command Line: " + process.getCommandLine());
            System.out.println("Environment Variables: ");
            Map<String, String> envs = process.getEnvironmentVariables();
            for (String envStr : envs.keySet()){
                System.out.println(envStr + ":" + envs.get(envStr)); 
            }
        }
    }

    /**
     * Test of getWindowsProcessId method, of class JnaNativeWindowsSupport.
     * Test by printing out the process ID and make sure the method works
     * and no exceptions are thrown
     */
    @Test
    public void testGetWindowsProcessId() throws IOException {
        if (!Functions.isWindows()) return;
        ProcessBuilder pb = new ProcessBuilder("C:/Program Files/Java/jre6/bin/java.exe");
        pb.directory(new File("C:/"));
        Process process = pb.start();
        JnaNativeWindowsSupport instance = new JnaNativeWindowsSupport();
        int result = instance.getWindowsProcessId(process);
        System.out.println(result);
    }

    /**
     * Test of windowsExec method, of class JnaNativeWindowsSupport.
     * Test by printing out the return result and make sure the method works
     * and no exceptions are thrown
     */
    @Test
    public void testWindowsExec() {
        if (!Functions.isWindows()) return;
        File winExe = new File ("C:/Program Files/Java/jre6/bin/java.exe");
        String args = "";
        String logFile = "";
        File pwd = new File("C:/");
        JnaNativeWindowsSupport instance = new JnaNativeWindowsSupport();
        int result = instance.windowsExec(winExe, args, logFile, pwd);
        System.out.println(result);
    }

    /**
     * Test of windowsMoveFile method, of class JnaNativeWindowsSupport.
     */
    @Test
    public void testWindowsMoveFile() throws IOException {
        if (!Functions.isWindows()) return;
        File fromFile = File.createTempFile("test", "test");
        System.out.println(fromFile);
        File tempDir = fromFile.getParentFile();
        File toFile = new File(tempDir, RandomStringUtils.randomAlphanumeric(8));
        System.out.println(toFile);
        JnaNativeWindowsSupport instance = new JnaNativeWindowsSupport();
        instance.windowsMoveFile(fromFile, toFile);
        assertTrue(toFile.exists());
    }
}
