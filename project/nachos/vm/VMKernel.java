package nachos.vm;

import java.util.Hashtable;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
	super();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
	super.initialize(args);
	virtualMemoryManager = new VirtualMemoryManager(PAGEFILE_NAME);
    }

    /**
     * Test this kernel.
     */	
    public void selfTest() {
	// test nothing;
    }

    /**
     * Start running user programs.
     */
    public void run() {
	super.run();
    }
    
    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
	virtualMemoryManager.dispose();
    }
    
    public static VirtualMemoryManager virtualMemoryManager = null;
    private static final String PAGEFILE_NAME = "_pagefile.swp";
    
    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;
}
