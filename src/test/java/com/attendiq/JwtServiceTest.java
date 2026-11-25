package com.attendiq;

import com.attendiq.web.AccountPrincipal;
import com.attendiq.web.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService service=new JwtService("test-signing-key-that-is-long-enough-for-hmac",8,new ObjectMapper());
    @Test void roleAndIdentitySurviveSignedTokenRoundTrip(){
        var account=new AccountPrincipal(42,"staff@campus.edu","STAFF","Faculty Person");
        var token=service.issue(account);
        assertEquals(account,service.verify(token.value()));
    }
    @Test void tamperedTokenIsRejected(){
        var token=service.issue(new AccountPrincipal(7,"s@campus.edu","STUDENT","Student"));
        int signatureStart=token.value().lastIndexOf('.')+1;
        char firstSignatureCharacter=token.value().charAt(signatureStart);
        String tampered=token.value().substring(0,signatureStart)
                +(firstSignatureCharacter=='a'?'b':'a')
                +token.value().substring(signatureStart+1);
        assertThrows(IllegalArgumentException.class,()->service.verify(tampered));
    }
}
