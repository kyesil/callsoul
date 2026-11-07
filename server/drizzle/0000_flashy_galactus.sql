CREATE TABLE "numbers" (
	"number" text PRIMARY KEY NOT NULL,
	"spam_votes" integer DEFAULT 0 NOT NULL,
	"safe_votes" integer DEFAULT 0 NOT NULL,
	"category" text,
	"caller_name" text,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "numbers" ENABLE ROW LEVEL SECURITY;--> statement-breakpoint
CREATE TABLE "reports" (
	"number" text NOT NULL,
	"device_hash" text NOT NULL,
	"is_spam" boolean NOT NULL,
	"category" text,
	"caller_name" text,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "reports_number_device_hash_pk" PRIMARY KEY("number","device_hash")
);
--> statement-breakpoint
ALTER TABLE "reports" ENABLE ROW LEVEL SECURITY;--> statement-breakpoint
ALTER TABLE "reports" ADD CONSTRAINT "reports_number_numbers_number_fk" FOREIGN KEY ("number") REFERENCES "public"."numbers"("number") ON DELETE cascade ON UPDATE no action;