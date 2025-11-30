package com.example.minibaseapp.crypto;

import java.util.Date;
public class ImportedCert {
    public final String alias;
    public final String subject;
    public final String issuer;
    public final Date notBefore;
    public final Date notAfter;
    public final boolean currentlyValid;

    public ImportedCert(String alias, String subject, String issuer,
                        Date notBefore, Date notAfter, boolean currentlyValid) {
        this.alias = alias;
        this.subject = subject;
        this.issuer = issuer;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.currentlyValid = currentlyValid;
    }
}
