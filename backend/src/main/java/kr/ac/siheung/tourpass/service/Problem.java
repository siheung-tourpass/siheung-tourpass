package kr.ac.siheung.tourpass.service;
public final class Problem extends RuntimeException {
    public final int status; public final String code;
    public Problem(int status, String code) { super(code); this.status=status; this.code=code; }
    public static void require(boolean valid, int status, String code) { if(!valid) throw new Problem(status,code); }
}
