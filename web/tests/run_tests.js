const tests = require("./webcam.test.js");

async function run() {
  const entries = Object.entries(tests);
  let failures = 0;

  for (const [name, test] of entries) {
    try {
      await test();
      console.log(`PASS: ${name}`);
    } catch (error) {
      failures += 1;
      console.error(`FAIL: ${name}`);
      console.error(error);
    }
  }

  if (failures > 0) {
    process.exit(1);
  }
}

run();
