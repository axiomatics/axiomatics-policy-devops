package com.myorg.alfa;

import com.axiomatics.cr.alfa.test.junit.AlfaExtension;
import com.axiomatics.cr.alfa.test.junit.TestRequest;
import com.axiomatics.cr.alfa.test.junit.TestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.axiomatics.cr.alfa.test.junit.matchers.AlfaMatchers.permit;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class MyConsultantPolicyUnitTest {


    @RegisterExtension
    public AlfaExtension alfa = new AlfaExtension().withMainPolicy("consultants.Consultants");

    @Test
    @DisplayName("REQ-ABAC-1: Should permit a Consultant to access resource in same location")
    public void shouldPermitIfUserAndResourceAreInSameLocation() {
        TestRequest target = alfa.newTestRequest()
                .with("user.role", "consultant")
                .with("user.location", "hawaii")
                .with("resource.location", "hawaii");

        TestResponse result = target.evaluate();

        assertThat(result, is(permit()));
    }

    @Test
    @DisplayName("REQ-ABAC-2: Should not permit a Consultant to access resource in another location")
    public void shouldNotPermitIfUserAndResourceAreInDifferentLocation() {
        TestRequest target = alfa.newTestRequest()
                .with("user.role", "consultant")
                .with("user.location", "hawaii")
                .with("resource.location", "stockholm");

        TestResponse result = target.evaluate();

        assertThat(result, is(not(permit())));
    }
}
