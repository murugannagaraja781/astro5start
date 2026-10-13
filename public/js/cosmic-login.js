// public/js/cosmic-login.js
// Astro5Star Premium Login & Country Validation Controller

(function() {
    'use strict';

    const DEFAULT_ENABLED_COUNTRIES = [
        { isoCode: 'IN', countryName: 'India', dialingCode: '+91', flag: '🇮🇳', currency: 'INR', currencySymbol: '₹', exampleNumber: '98765 43210' },
        { isoCode: 'US', countryName: 'United States', dialingCode: '+1', flag: '🇺🇸', currency: 'USD', currencySymbol: '$', exampleNumber: '(201) 555-0123' },
        { isoCode: 'GB', countryName: 'United Kingdom', dialingCode: '+44', flag: '🇬🇧', currency: 'GBP', currencySymbol: '£', exampleNumber: '07911 123456' },
        { isoCode: 'CA', countryName: 'Canada', dialingCode: '+1', flag: '🇨🇦', currency: 'CAD', currencySymbol: 'CA$', exampleNumber: '(416) 555-0123' },
        { isoCode: 'AU', countryName: 'Australia', dialingCode: '+61', flag: '🇦🇺', currency: 'AUD', currencySymbol: 'A$', exampleNumber: '0412 345 678' },
        { isoCode: 'AE', countryName: 'United Arab Emirates', dialingCode: '+971', flag: '🇦🇪', currency: 'AED', currencySymbol: 'AED', exampleNumber: '050 123 4567' },
        { isoCode: 'SA', countryName: 'Saudi Arabia', dialingCode: '+966', flag: '🇸🇦', currency: 'SAR', currencySymbol: 'SAR', exampleNumber: '050 123 4567' },
        { isoCode: 'SG', countryName: 'Singapore', dialingCode: '+65', flag: '🇸🇬', currency: 'SGD', currencySymbol: 'S$', exampleNumber: '9123 4567' },
        { isoCode: 'MY', countryName: 'Malaysia', dialingCode: '+60', flag: '🇲🇾', currency: 'MYR', currencySymbol: 'RM', exampleNumber: '012-345 6789' },
        { isoCode: 'LK', countryName: 'Sri Lanka', dialingCode: '+94', flag: '🇱🇰', currency: 'LKR', currencySymbol: 'Rs', exampleNumber: '071 234 5678' }
    ];

    let countriesList = [...DEFAULT_ENABLED_COUNTRIES];
    let selectedCountry = countriesList[0];
    let resendTimerInterval = null;
    let resendSecondsRemaining = 0;

    /**
     * Initializes the login UI and fetches enabled countries from server.
     */
    async function initCosmicLogin() {
        try {
            const res = await fetch('/api/countries/enabled');
            const data = await res.json();
            if (data.ok && Array.isArray(data.countries) && data.countries.length > 0) {
                // Alphabetical sort by country name
                countriesList = data.countries.sort((a, b) => a.countryName.localeCompare(b.countryName));
                
                // Keep selected country or default to India (+91)
                const inCountry = countriesList.find(c => c.isoCode === 'IN') || countriesList[0];
                selectedCountry = inCountry;
            }
        } catch (e) {
            console.warn('[CosmicLogin] Could not fetch enabled countries, using fallback list', e);
        }

        renderCountryTrigger();
        renderCountryModalList();
        bindEvents();
    }

    function renderCountryTrigger() {
        const flagEl = document.getElementById('cosmicSelectedFlag');
        const codeEl = document.getElementById('cosmicSelectedCode');
        const phoneInput = document.getElementById('inputPhone');

        if (flagEl) flagEl.textContent = selectedCountry.flag || '🌐';
        if (codeEl) codeEl.textContent = selectedCountry.dialingCode || '+91';
        
        if (phoneInput) {
            const ex = selectedCountry.exampleNumber || 'Mobile Number';
            phoneInput.placeholder = `e.g. ${ex}`;
        }

        // Also sync hidden countrySelect for backwards compatibility with any existing scripts
        const legacySelect = document.getElementById('countrySelect');
        if (legacySelect) {
            legacySelect.value = selectedCountry.dialingCode;
        }

        validateCurrentInput();
    }

    function renderCountryModalList(filterQuery = '') {
        const listEl = document.getElementById('cosmicCountryList');
        if (!listEl) return;

        const q = filterQuery.toLowerCase().trim();
        const filtered = countriesList.filter(c => {
            if (!q) return true;
            return c.countryName.toLowerCase().includes(q) ||
                   c.isoCode.toLowerCase().includes(q) ||
                   c.dialingCode.includes(q);
        });

        if (filtered.length === 0) {
            listEl.innerHTML = `<div style="padding: 24px; text-align: center; color: rgba(255,255,255,0.4); font-size: 0.85rem;">No enabled countries found matching "${filterQuery}"</div>`;
            return;
        }

        listEl.innerHTML = filtered.map(c => {
            const isSel = c.isoCode === selectedCountry.isoCode;
            return `
                <div class="cosmic-country-item ${isSel ? 'selected' : ''}" data-iso="${c.isoCode}">
                    <div class="cosmic-country-item-left">
                        <span class="cosmic-country-item-flag">${c.flag || '🌐'}</span>
                        <div>
                            <span class="cosmic-country-item-name">${escapeText(c.countryName)}</span>
                            <span class="cosmic-country-item-iso">(${c.isoCode})</span>
                        </div>
                    </div>
                    <span class="cosmic-country-item-code">${c.dialingCode}</span>
                </div>
            `;
        }).join('');

        // Attach click listeners to rows
        listEl.querySelectorAll('.cosmic-country-item').forEach(item => {
            item.onclick = () => {
                const iso = item.dataset.iso;
                const found = countriesList.find(c => c.isoCode === iso);
                if (found) {
                    selectedCountry = found;
                    renderCountryTrigger();
                    closeCountryModal();
                    const phoneInput = document.getElementById('inputPhone');
                    if (phoneInput) phoneInput.focus();
                }
            };
        });
    }

    function openCountryModal() {
        const modal = document.getElementById('cosmicCountryModal');
        const searchInput = document.getElementById('cosmicCountrySearch');
        if (modal) {
            modal.classList.add('open');
            if (searchInput) {
                searchInput.value = '';
                searchInput.focus();
            }
            renderCountryModalList();
        }
    }

    function closeCountryModal() {
        const modal = document.getElementById('cosmicCountryModal');
        if (modal) modal.classList.remove('open');
    }

    /**
     * Client-side phone validation using libphonenumber-js/max if available,
     * otherwise checking reasonable length based on example number.
     */
    function validatePhone(phoneStr, countryIso, dialCode) {
        const cleanDigits = String(phoneStr || '').replace(/\D/g, '');
        if (!cleanDigits) return { isValid: false, isPossible: false, message: 'Please enter mobile number' };

        // Test accounts bypass
        if (['8000000001', '9000000001', '9876543210', '2125551234'].includes(cleanDigits)) {
            return { isValid: true, isPossible: true, message: 'Test account recognized', e164: `${dialCode}${cleanDigits}` };
        }

        // Use window.libphonenumber if loaded
        if (window.libphonenumber && typeof window.libphonenumber.parsePhoneNumberFromString === 'function') {
            try {
                // If user entered with dial code or +, parse directly
                let parsed = null;
                if (phoneStr.startsWith('+')) {
                    parsed = window.libphonenumber.parsePhoneNumberFromString(phoneStr, countryIso);
                } else {
                    parsed = window.libphonenumber.parsePhoneNumberFromString(`${dialCode}${cleanDigits}`, countryIso);
                }

                if (parsed) {
                    const isPossible = parsed.isPossible();
                    const isValid = parsed.isValid();
                    return {
                        isValid,
                        isPossible,
                        e164: parsed.number,
                        national: parsed.formatNational ? parsed.formatNational() : cleanDigits,
                        message: isValid ? 'Valid phone number' : (isPossible ? 'Number format looks incomplete' : 'Invalid number length')
                    };
                }
            } catch (err) {}
        }

        // Fallback length heuristics if libphonenumber-max not yet initialized
        const len = cleanDigits.length;
        if (len >= 7 && len <= 15) {
            return { isValid: true, isPossible: true, message: 'Number looks valid', e164: `${dialCode}${cleanDigits}` };
        }
        return { isValid: false, isPossible: false, message: 'Please enter a valid mobile number' };
    }

    function validateCurrentInput() {
        const phoneInput = document.getElementById('inputPhone');
        const hintEl = document.getElementById('cosmicPhoneHint');
        const btn = document.getElementById('btnSendOtp');
        if (!phoneInput) return;

        const val = phoneInput.value.trim();
        if (!val) {
            if (hintEl) {
                hintEl.className = 'cosmic-phone-hint';
                hintEl.textContent = `Enter mobile number for ${selectedCountry.countryName}`;
            }
            if (btn) btn.disabled = false;
            return;
        }

        const res = validatePhone(val, selectedCountry.isoCode, selectedCountry.dialingCode);
        if (hintEl) {
            hintEl.className = `cosmic-phone-hint ${res.isValid ? 'valid' : (val.length >= 6 ? 'error' : '')}`;
            hintEl.innerHTML = res.isValid 
                ? `<i class="fas fa-check-circle"></i> Valid number for ${selectedCountry.countryName}`
                : `<i class="fas fa-circle-info"></i> ${res.message}`;
        }
    }

    function startResendCountdown() {
        resendSecondsRemaining = 30;
        const resendBtn = document.getElementById('cosmicResendOtpBtn');
        const timerText = document.getElementById('cosmicResendTimer');
        if (!resendBtn) return;

        resendBtn.disabled = true;
        if (timerText) timerText.textContent = ` (${resendSecondsRemaining}s)`;

        clearInterval(resendTimerInterval);
        resendTimerInterval = setInterval(() => {
            resendSecondsRemaining--;
            if (resendSecondsRemaining <= 0) {
                clearInterval(resendTimerInterval);
                resendBtn.disabled = false;
                if (timerText) timerText.textContent = '';
            } else {
                if (timerText) timerText.textContent = ` (${resendSecondsRemaining}s)`;
            }
        }, 1000);
    }

    function bindEvents() {
        // Trigger opens modal
        const trigger = document.getElementById('cosmicCountryTrigger');
        if (trigger) trigger.onclick = openCountryModal;

        // Close modal
        const closeBtn = document.getElementById('cosmicCountryClose');
        if (closeBtn) closeBtn.onclick = closeCountryModal;

        const modal = document.getElementById('cosmicCountryModal');
        if (modal) {
            modal.onclick = (e) => {
                if (e.target === modal) closeCountryModal();
            };
        }

        // Search in modal
        const searchInput = document.getElementById('cosmicCountrySearch');
        if (searchInput) {
            searchInput.oninput = (e) => renderCountryModalList(e.target.value);
        }

        // Phone input live validation (no fixed length limitation!)
        const phoneInput = document.getElementById('inputPhone');
        if (phoneInput) {
            phoneInput.oninput = () => {
                phoneInput.value = phoneInput.value.replace(/[^0-9]/g, '');
                validateCurrentInput();
            };
        }

        // Change phone number button in OTP step
        const changeNumberBtn = document.getElementById('cosmicChangeNumberBtn');
        if (changeNumberBtn) {
            changeNumberBtn.onclick = () => {
                document.getElementById('step-otp').classList.add('hidden');
                document.getElementById('step-phone').classList.remove('hidden');
                clearInterval(resendTimerInterval);
                if (phoneInput) phoneInput.focus();
            };
        }

        // Resend OTP button
        const resendBtn = document.getElementById('cosmicResendOtpBtn');
        if (resendBtn) {
            resendBtn.onclick = () => {
                const sendBtn = document.getElementById('btnSendOtp');
                if (sendBtn) sendBtn.click();
            };
        }
    }

    function escapeText(str) {
        if (!str) return '';
        return String(str).replace(/[&<>"']/g, function(m) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m];
        });
    }

    // Expose helpers globally
    window.CosmicLogin = {
        init: initCosmicLogin,
        getSelectedCountry: () => selectedCountry,
        validatePhone,
        startResendCountdown
    };

    // Auto-init when DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initCosmicLogin);
    } else {
        initCosmicLogin();
    }
})();
