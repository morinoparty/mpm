import { Scalar } from "@scalar/hono-api-reference";
import { Hono } from "hono";

const openAPIRouter = new Hono();

openAPIRouter.get("/scalar", Scalar({ url: "/openapi" }));

export default openAPIRouter;