// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.model

/** A plain lat/lon (no Maps SDK, no network) — the offline map coordinate. */
data class GeoCoord(val lat: Double, val lon: Double)
