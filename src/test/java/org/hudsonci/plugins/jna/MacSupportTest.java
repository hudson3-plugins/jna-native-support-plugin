/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hudsonci.plugins.jna;

import hudson.util.jna.NativeProcess;
import java.util.List;
import java.util.Map;
import org.junit.*;

/**
 *
 * @author winstonp
 */
public class MacSupportTest {

    /**
     * Test of getMacProcesses method, of class JnaNativeMacSupport.
     */
    @Test
    public void testGetMacProcesses() {
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
            process.getPid();
        }
    }
}
