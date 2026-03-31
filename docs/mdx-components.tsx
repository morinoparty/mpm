import type { MDXComponents } from "mdx/types";
import defaultComponents from "fumadocs-ui/mdx";
import { Mermaid } from "./components/mdx/mermaid";
import { CommandLine } from "@/components/command-line";
import { CommandSection } from "@/components/command-section";
import { CommandList } from "@/components/command-list";
import { RepoFileGenerator } from "@/components/repo-file-generator";

const customComponents = {
    CommandLine,
    CommandSection,
    CommandList,
    RepoFileGenerator,
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
