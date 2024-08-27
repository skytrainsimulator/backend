package io.u11.skytrainsim.backend.util

import org.springframework.http.ResponseEntity

val <T> T?.responseEntity get() = ResponseEntity.ok<T>(this)
