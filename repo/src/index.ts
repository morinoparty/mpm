import { Hono } from "hono";
import {
    describeRoute,
    openAPIRouteHandler,
    resolver, validator,
} from "hono-openapi";
import { z } from "zod";
import { PluginInfoSchema} from "./type/plugin-info";
import dayjs from "dayjs";
import timezone from "dayjs/plugin/timezone.js";
import utc from "dayjs/plugin/utc.js";
import list from "../public/paper/_list.json"
import { trimTrailingSlash } from "hono/trailing-slash";
dayjs.extend(utc);
dayjs.extend(timezone);

import openAPIRouter from "./open-api";

const app = new Hono();

app.use(trimTrailingSlash());

openAPIRouter.get(
    "/openapi",
    openAPIRouteHandler(app, {
        documentation: {
            info: {
                title: "morinoparty mpm API",
                version: "1.0.0",
                description: "mpm repository for morinoparty",
            },
        },
    }),
);


app.get(
    "/paper/list",
    async (c) => {
        return c.json(list);
    },

    describeRoute({
        description: "現在時刻を返す",
        responses: {
            200: {
                description: "現在時刻を返す",
                content: {
                    "text/plain": { schema: resolver(z.string()) },
                },
            },
        },
    }),
);

const requestSchema = z.object({
    pluginId: z.string().default("MinecraftPluginManager.json"),
});

app.get(
    "/paper/plugins/:pluginId",
    validator("param", requestSchema),
    async (c) => {
        const pluginId = c.req.param("pluginId");
        const plugin = list.find((p) => p === pluginId);
        if (!plugin) {
            return c.json({ error: "Plugin not found" }, 404);
        }
        return c.json(plugin);
    },
    describeRoute({
        description: "プラグイン情報を返す",

        responses: {
            200: {
                description: 'Successful response',
                content: {
                    'text/plain': {schema: resolver(PluginInfoSchema)},
                },
            },
        },
    }
));

app.route("/", openAPIRouter);

export default app;