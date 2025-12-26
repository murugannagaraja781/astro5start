const express = require("express");
const router = express.Router();
const {
    calculatePlanetaryPositions,
    createDate,
} = require("vedic-astrology-api/lib/utils/common");
const {
    computeMahadashaSequence,
    computeBhuktisForMahadasha,
    getCurrentDasha,
} = require("../utils/vimshottari");

/**
 * POST /api/vimshottari
 * Calculate Vimshottari Dasha (accepts POST body with parameters)
 */
router.post("/", (req, res) => {
    try {
        const {
            year,
            month,
            day,
            hour = 0,
            minute = 0,
            lat,
            lon,
            tz = 5.5,
            endpoint = "complete",
        } = req.body;

        if (!year || !month || !day || lat === undefined || lon === undefined) {
            return res.status(400).json({
                error: "Missing required parameters: year, month, day, lat, lon",
            });
        }

        // Use createDate from util which handles timezones correctly if applicable, or generic Date
        const date = new Date(year, month - 1, day, hour, minute); // Simple construction

        // NOTE: calculatePlanetaryPositions from vedic-astrology-api might need checking
        const { positions } = require('../utils/astroCalculations').generatePlanetaryPositions(date, lat, lon); // Adapter
        const moonLong = positions.Moon.longitude || positions.Moon;

        let result;

        if (endpoint === "mahadashas") {
            const mahadashas = computeMahadashaSequence(date, moonLong);
            result = {
                input: { year, month, day, hour, minute, lat, lon, tz },
                moonLongitude: moonLong,
                mahadashas: mahadashas.map((m) => ({
                    lord: m.lord,
                    start: m.start.toISOString(),
                    end: m.end.toISOString(),
                    years: m.years.toFixed(2),
                })),
            };
        } else if (endpoint === "current") {
            const targetDate = req.body.targetDate
                ? new Date(req.body.targetDate)
                : new Date();
            const current = getCurrentDasha(date, moonLong, targetDate);

            if (current.error) {
                return res.status(400).json(current);
            }

            result = {
                birthDate: date.toISOString(),
                targetDate: targetDate.toISOString(),
                moonLongitude: moonLong,
                current: {
                    mahadasha: {
                        lord: current.mahadasha.lord,
                        start: current.mahadasha.start.toISOString(),
                        end: current.mahadasha.end.toISOString(),
                    },
                    bhukti: {
                        lord: current.bhukti.subLord,
                        start: current.bhukti.start.toISOString(),
                        end: current.bhukti.end.toISOString(),
                    },
                    pratyantar: current.pratyantar
                        ? {
                            lord: current.pratyantar.pratyantarLord,
                            start: current.pratyantar.start.toISOString(),
                            end: current.pratyantar.end.toISOString(),
                        }
                        : null,
                },
            };
        } else {
            // default: complete
            const mahadashas = computeMahadashaSequence(date, moonLong);
            const complete = mahadashas.map((maha) => {
                const bhuktis = computeBhuktisForMahadasha(maha);
                return {
                    lord: maha.lord,
                    start: maha.start.toISOString(),
                    end: maha.end.toISOString(),
                    years: maha.years.toFixed(2),
                    bhuktis: bhuktis.map((b) => ({
                        lord: b.subLord,
                        start: b.start.toISOString(),
                        end: b.end.toISOString(),
                        years: b.years.toFixed(3),
                    })),
                };
            });

            result = {
                input: { year, month, day, hour, minute, lat, lon, tz },
                moonLongitude: moonLong,
                dashaSystem: complete,
            };
        }

        res.json(result);
    } catch (err) {
        console.error(err);
        res
            .status(500)
            .json({ error: err.message || "Error in Dasha calculation" });
    }
});

/**
 * GET /api/vimshottari/mahadashas
 * Get complete Mahadasha sequence
 */
router.get("/mahadashas", (req, res) => {
    try {
        const year = Number(req.query.year);
        const month = Number(req.query.month);
        const day = Number(req.query.day);
        const hour = Number(req.query.hour || 0);
        const minute = Number(req.query.minute || 0);
        const lat = Number(req.query.lat);
        const lon = Number(req.query.lon);
        const tz = Number(req.query.tz || 5.5);

        const date = new Date(year, month - 1, day, hour, minute);
        // Adapter call
        const positions = require('../utils/astroCalculations').generatePlanetaryPositions(date, lat, lon);
        const moonLong = positions.Moon;

        const mahadashas = computeMahadashaSequence(date, moonLong);

        res.json({
            input: { year, month, day, hour, minute, lat, lon, tz },
            moonLongitude: moonLong,
            mahadashas: mahadashas.map((m) => ({
                lord: m.lord,
                start: m.start.toISOString(),
                end: m.end.toISOString(),
                years: m.years.toFixed(2),
            })),
        });
    } catch (err) {
        console.error(err);
        res
            .status(500)
            .json({ error: err.message || "Error in Mahadasha calculation" });
    }
});

/**
 * GET /api/vimshottari/bhuktis
 * Get Bhuktis for a specific Mahadasha
 */
router.get("/bhuktis", (req, res) => {
    try {
        const year = Number(req.query.year);
        const month = Number(req.query.month);
        const day = Number(req.query.day);
        const hour = Number(req.query.hour || 0);
        const minute = Number(req.query.minute || 0);
        const lat = Number(req.query.lat);
        const lon = Number(req.query.lon);
        const tz = Number(req.query.tz || 5.5);
        const mahaIndex = Number(req.query.mahaIndex || 0);

        const date = new Date(year, month - 1, day, hour, minute);
        const positions = require('../utils/astroCalculations').generatePlanetaryPositions(date, lat, lon);
        const moonLong = positions.Moon;


        const mahadashas = computeMahadashaSequence(date, moonLong);
        const selectedMaha = mahadashas[mahaIndex];

        if (!selectedMaha) {
            return res.status(400).json({ error: "Invalid Mahadasha index" });
        }

        const bhuktis = computeBhuktisForMahadasha(selectedMaha);

        res.json({
            mahadasha: {
                lord: selectedMaha.lord,
                start: selectedMaha.start.toISOString(),
                end: selectedMaha.end.toISOString(),
            },
            bhuktis: bhuktis.map((b) => ({
                mahaLord: b.mahaLord,
                subLord: b.subLord,
                start: b.start.toISOString(),
                end: b.end.toISOString(),
                years: b.years.toFixed(3),
            })),
        });
    } catch (err) {
        console.error(err);
        res
            .status(500)
            .json({ error: err.message || "Error in Bhukti calculation" });
    }
});

/**
 * GET /api/vimshottari/current
 * Get current running Dasha-Bhukti-Pratyantar
 */
router.get("/current", (req, res) => {
    try {
        const year = Number(req.query.year);
        const month = Number(req.query.month);
        const day = Number(req.query.day);
        const hour = Number(req.query.hour || 0);
        const minute = Number(req.query.minute || 0);
        const lat = Number(req.query.lat);
        const lon = Number(req.query.lon);
        const tz = Number(req.query.tz || 5.5);

        const birthDate = new Date(year, month - 1, day, hour, minute);
        const positions = require('../utils/astroCalculations').generatePlanetaryPositions(birthDate, lat, lon);
        const moonLong = positions.Moon;

        // Target date (default: today)
        const targetDate = req.query.targetDate
            ? new Date(req.query.targetDate)
            : new Date();

        const current = getCurrentDasha(birthDate, moonLong, targetDate);

        if (current.error) {
            return res.status(400).json(current);
        }

        res.json({
            birthDate: birthDate.toISOString(),
            targetDate: targetDate.toISOString(),
            moonLongitude: moonLong,
            current: {
                mahadasha: {
                    lord: current.mahadasha.lord,
                    start: current.mahadasha.start.toISOString(),
                    end: current.mahadasha.end.toISOString(),
                },
                bhukti: {
                    lord: current.bhukti.subLord,
                    start: current.bhukti.start.toISOString(),
                    end: current.bhukti.end.toISOString(),
                },
                pratyantar: current.pratyantar
                    ? {
                        lord: current.pratyantar.pratyantarLord,
                        start: current.pratyantar.start.toISOString(),
                        end: current.pratyantar.end.toISOString(),
                    }
                    : null,
            },
        });
    } catch (err) {
        console.error(err);
        res
            .status(500)
            .json({ error: err.message || "Error in current Dasha calculation" });
    }
});

/**
 * GET /api/vimshottari/complete
 * Get complete Dasha system (Maha + all Bhuktis)
 */
router.get("/complete", (req, res) => {
    try {
        const year = Number(req.query.year);
        const month = Number(req.query.month);
        const day = Number(req.query.day);
        const hour = Number(req.query.hour || 0);
        const minute = Number(req.query.minute || 0);
        const lat = Number(req.query.lat);
        const lon = Number(req.query.lon);
        const tz = Number(req.query.tz || 5.5);

        const date = new Date(year, month - 1, day, hour, minute);
        const positions = require('../utils/astroCalculations').generatePlanetaryPositions(date, lat, lon);
        const moonLong = positions.Moon;

        const mahadashas = computeMahadashaSequence(date, moonLong);

        const complete = mahadashas.map((maha) => {
            const bhuktis = computeBhuktisForMahadasha(maha);
            return {
                lord: maha.lord,
                start: maha.start.toISOString(),
                end: maha.end.toISOString(),
                years: maha.years.toFixed(2),
                bhuktis: bhuktis.map((b) => ({
                    lord: b.subLord,
                    start: b.start.toISOString(),
                    end: b.end.toISOString(),
                    years: b.years.toFixed(3),
                })),
            };
        });

        res.json({
            input: { year, month, day, hour, minute, lat, lon, tz },
            moonLongitude: moonLong,
            dashaSystem: complete,
        });
    } catch (err) {
        console.error(err);
        res
            .status(500)
            .json({ error: err.message || "Error in complete Dasha calculation" });
    }
});

module.exports = router;
