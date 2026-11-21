package com.dmu.dshorts.domain.usage;

import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final UsageService usage;

    public UsageController(UsageService usage) {
        this.usage = usage;
    }

    @GetMapping("/today")
    public ApiResponse<UsageService.Stats> today(@CurrentUser(required = true) AuthUser user) {
        return ApiResponse.of(usage.today(user.id()));
    }

    @PutMapping("/today/pledge")
    public ApiResponse<UsageService.Stats> pledge(@CurrentUser(required = true) AuthUser user,
                                                @Valid @RequestBody PledgeInput input) {
        return ApiResponse.of(usage.pledge(user.id(), input.pledgedCount()));
    }

    @PostMapping("/bundles")
    public ApiResponse<UsageService.BundleResult> bundle(@CurrentUser AuthUser user,
                                                       @RequestBody BundleInput input) {
        return ApiResponse.of(usage.bundle(user, input.postIds()));
    }

    @PostMapping("/stops")
    public ApiResponse<UsageService.Stats> stop(@CurrentUser(required = true) AuthUser user) {
        return ApiResponse.of(usage.stop(user.id()));
    }

    public record PledgeInput(@NotNull @Min(1) @Max(100) Integer pledgedCount) {}
    public record BundleInput(List<Long> postIds) {}
}
