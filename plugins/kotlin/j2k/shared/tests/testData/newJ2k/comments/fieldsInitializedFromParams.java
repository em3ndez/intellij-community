// IGNORE_K2
class C {
    private final int p1; // field p1

    /**
     * Field myP2
     */
    private final int myP2;

    /* Field p3 */ public int p3;

    public C(int p1 /* parameter p1 */, int p2, int p3) {
        this.p1 = p1;
        myP2 = p2;
        this.p3 = p3;
    }
}
