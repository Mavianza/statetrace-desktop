package statetrace;

public final class Samples {
    private Samples() { }

    public static final String SUFFIX_01 = """
            # Bahasa: semua string biner yang berakhiran 01
            name=Berakhiran 01
            states=q0,q1,q2
            alphabet=0,1
            start=q0
            accept=q2

            q0,0=q1
            q0,1=q0
            q1,0=q1
            q1,1=q2
            q2,0=q1
            q2,1=q0
            """;

    public static final String EVEN_ONES = """
            # Bahasa: semua string biner dengan jumlah 1 genap
            name=Jumlah 1 genap
            states=genap,ganjil
            alphabet=0,1
            start=genap
            accept=genap

            genap,0=genap
            genap,1=ganjil
            ganjil,0=ganjil
            ganjil,1=genap
            """;
}
