import type { MDXComponents } from "mdx/types";
import defaultComponents from "fumadocs-ui/mdx";
import { Mermaid } from "./components/mdx/mermaid";

const customComponents = {
};

export function getMDXComponents(components?: MDXComponents): MDXComponents {
    return {
        ...defaultComponents,
        Mermaid,
        ...customComponents,
        ...components,
    };
}

export function useMDXComponents(components: MDXComponents): MDXComponents {
    return {
        ...defaultComponents,
        Mermaid,
        ...customComponents,
        ...components,
    };
}
