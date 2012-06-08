/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hudsonci.plugins.jna;

import hudson.Functions;
import hudson.Util;
import java.util.List;
import java.util.Map;
import org.eclipse.hudson.jna.NativeProcess;
import org.junit.*;

/**
 * Unit Test for Mac processes of JNA based Native Mac Support
 * These tests can only run on a Mac machine
 * 
 * @author winstonp
 */
public class MacSupportTest {

    /**
     * Test of getMacProcesses method, of class JnaNativeMacSupport.
     * Nothing special but simply prints process info and verifies the method works
     */
    @Test
    public void testGetMacProcesses() {
        if (Functions.isWindows()) return;
        String osName = Util.fixNull(System.getProperty("os.name"));
        if (!osName.contains("Mac")) return; 
        System.out.println("Testing  Get MAC Processes");
        JnaNativeMacSupport instance = new JnaNativeMacSupport();
        List<NativeProcess> result = instance.getMacProcesses();
        for (NativeProcess process : result){
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
}
