package nachos.vm;

import java.util.Hashtable;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;
import nachos.userprog.MemoryManager;

public class PageFile {
    public PageFile(String filename) {
	lock = new Lock();
	lock.acquire();
	freepages = new MemoryManager(0);
	swapfile = ThreadedKernel.fileSystem.open(filename, true);
	hashtable = new Hashtable();
	this.filename = filename;
	lock.release();
    }
    
    public void close() {
	swapfile.close();
	swapfile = null;

	ThreadedKernel.fileSystem.remove(filename);
    }
    
    public boolean containsEntry(int pid, int vpn) {
	lock.acquire();
	VirtualAddress key = new VirtualAddress(pid, vpn);
	boolean retval = hashtable.containsKey(key);
	lock.release();
	return retval;
    }
    
    public boolean write(int pid, TranslationEntry entry) {
	lock.acquire();
	if (freepages.freepageCount() < 1) 
	    expand(DLT_EXP);
	
	VirtualAddress key = new VirtualAddress(pid, entry.vpn);
	boolean newpage = false;
	TranslationEntry page = (TranslationEntry)hashtable.get(key);
	if (page == null) {
	    newpage = true;
	    page = freepages.allocPages(1)[0];
	    hashtable.put(key, page);
	}

	page.vpn = entry.vpn;
        byte[] memory = Machine.processor().getMemory();
        int paddr = entry.ppn * pageSize;
        int faddr = page.ppn * pageSize;
        int bytesWritten = swapfile.write(faddr, memory, paddr, pageSize);
        if (bytesWritten != pageSize) {
            if (newpage) free(pid, page.vpn);
            lock.release();
            return false;
        }
	
	lock.release();
	return true;
    }

    public boolean read(int pid, int vpn, int ppn) {
	lock.acquire();

	VirtualAddress key = new VirtualAddress(pid, vpn);
	TranslationEntry page = (TranslationEntry)hashtable.get(key);
	
	byte[] memory = Machine.processor().getMemory();
        int paddr = ppn * pageSize;
        int faddr = page.ppn * pageSize;
        int bytesRead = swapfile.read(faddr, memory, paddr, pageSize);
        if (bytesRead != pageSize) {
            lock.release();
    	    return false;
        }
	
	lock.release();
	return true;
    }
    
    public void free(int pid, int vpn) {
	lock.acquire();
	VirtualAddress key = new VirtualAddress(pid, vpn);
	hashtable.remove(key);
	TranslationEntry [] entries = new TranslationEntry[1];
	freepages.deallocPages(entries);
	lock.release();
    }
    
    private void expand(int n) {
	TranslationEntry []entry = new TranslationEntry[n];
	for (int i = 0; i < n; ++i) {
	    entry[i] = new TranslationEntry();
	    entry[i].ppn = pageNum++;
	    entry[i].valid = true;
	}
	freepages.deallocPages(entry);
    }
    
    private MemoryManager freepages = null;
    private OpenFile swapfile = null;
    private Hashtable hashtable = null;
    private int pageNum = 0;
    private Lock lock = null;
    private String filename;
    
    private static final int DLT_EXP = 10;
    private static final int pageSize = Processor.pageSize;
}
