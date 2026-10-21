import { createFileRoute } from "@tanstack/react-router";
import { Studio } from "@/components/studio/studio";

export const Route = createFileRoute("/")({
  component: Studio,
  head: () => ({
    meta: [
      { title: "Sandors Mirrorcast" },
      {
        name: "description",
        content: "Pair a Pixel 10 to a Fire Stick with a PIN, then mirror H.264 and system audio across the room.",
      },
    ],
  }),
});
