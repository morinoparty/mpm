module.exports = function tailwindPlugin(context, options) {
    return {
        name: "tailwind-plugin",
        // injectHtmlTags() {
        //     return {
        //         headTags: [
        //             {
        //                 tagName: "link",
        //                 attributes: {
        //                     rel: "stylesheet",
        //                     href: "https://unpkg.com/tailwindcss@4.0.9/preflight.css",
        //                 },
        //             },
        //         ],
        //     };
        // },
        configurePostCss(postcssOptions) {
            postcssOptions.plugins = [require("@tailwindcss/postcss")];
            return postcssOptions;
        },
    };
};
