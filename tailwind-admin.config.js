/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ["./public/superadmin.html"],
    theme: {
        extend: {
            colors: {
                'milk': '#f0fdf4',
                'brand-green': '#059669',
                'deep-green': '#064e3b',
                'accent-green': '#10b981',
            },
            fontFamily: {
                sans: ['Outfit', 'sans-serif'],
            },
            boxShadow: {
                'premium': '0 10px 15px -3px rgba(5, 150, 105, 0.1), 0 4px 6px -2px rgba(5, 150, 105, 0.05)',
            }
        }
    },
    plugins: [
        require('@tailwindcss/forms'),
        require('@tailwindcss/typography'),
        require('@tailwindcss/aspect-ratio'),
    ],
}
