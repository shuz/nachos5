package nachos.vm;

class VirtualAddress {
    VirtualAddress(int pid, int vpn) {
        this.pid = pid;
        this.vpn = vpn;
    }
    
    int pid, vpn;
    
    public int hashCode() {
        return pid ^ ~vpn;
    }
    
    public boolean equals(Object obj) {
        if (obj instanceof VirtualAddress) {
    	    VirtualAddress rhs = (VirtualAddress)obj;
    	    return pid == rhs.pid && vpn == rhs.vpn;
        }
        return false;
    }
}