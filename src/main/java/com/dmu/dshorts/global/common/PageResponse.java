package com.dmu.dshorts.global.common;

import java.util.List;

public record PageResponse<T>(List<T> items, String nextCursor) {}
