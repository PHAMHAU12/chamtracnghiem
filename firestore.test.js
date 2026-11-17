const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

function serverTimestamp() {
  return new Date(Date.now() - 2000);
}

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-chmthiai-28cdf617-a72b-4421-abe0-62972eea01c4";
const ALICE_UID = "alice_teacher_1";
const BOB_UID = "bob_teacher_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function aliceDb() {
  return testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
}

function bobDb() {
  return testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
}

function unauthDb() {
  return testEnv.unauthenticatedContext().firestore(DATABASE_ID);
}

function validExamPayload(uid = ALICE_UID) {
  return {
    userId: uid,
    title: "Kiểm tra giữa kỳ Toán 10",
    subject: "Toán học",
    totalQuestions: 5,
    maxScore: 10.0,
    answerKey: ["A", "B", "C", "D", "A"],
    submissionCount: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validSubmissionPayload(uid = ALICE_UID, examId = "exam_1") {
  return {
    userId: uid,
    examId: examId,
    studentName: "Nguyễn Minh Anh",
    studentCode: "SBD001",
    studentAnswers: ["A", "B", "D", "D", "A"],
    correctCount: 4,
    wrongCount: 1,
    blankCount: 0,
    score: 8.0,
    notes: "Rõ nét",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

test("Unauthenticated user cannot read or create exams", async () => {
  await assertFails(unauthDb().collection(`users/${ALICE_UID}/exams`).get());
  await assertFails(
    unauthDb().collection(`users/${ALICE_UID}/exams`).doc("exam_1").set(validExamPayload())
  );
});

test("Owner can create, read, list, update, and delete exam", async () => {
  const db = aliceDb();
  const examRef = db.collection(`users/${ALICE_UID}/exams`).doc("exam_1");

  await assertSucceeds(examRef.set(validExamPayload()));
  await assertSucceeds(examRef.get());
  await assertSucceeds(db.collection(`users/${ALICE_UID}/exams`).get());

  await assertSucceeds(
    examRef.update({
      title: "Kiểm tra giữa kỳ Toán 10 - Đề A",
      submissionCount: 1,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(examRef.delete());
});

test("Cross-user access to exams and submissions is denied", async () => {
  const aDb = aliceDb();
  const bDb = bobDb();
  const examRef = aDb.collection(`users/${ALICE_UID}/exams`).doc("exam_1");
  await assertSucceeds(examRef.set(validExamPayload()));

  await assertFails(bDb.collection(`users/${ALICE_UID}/exams`).doc("exam_1").get());
  await assertFails(bDb.collection(`users/${ALICE_UID}/exams`).get());
  await assertFails(
    bDb.collection(`users/${ALICE_UID}/exams`).doc("exam_2").set(validExamPayload(BOB_UID))
  );
  await assertFails(
    bDb.collection(`users/${ALICE_UID}/exams`).doc("exam_1").update({
      title: "Hacked",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(bDb.collection(`users/${ALICE_UID}/exams`).doc("exam_1").delete());
});

test("Rejects exam with ghost field or mismatched answerKey size", async () => {
  const db = aliceDb();
  await assertFails(
    db.collection(`users/${ALICE_UID}/exams`).doc("exam_ghost").set({
      ...validExamPayload(),
      isAdmin: true,
    })
  );
  await assertFails(
    db.collection(`users/${ALICE_UID}/exams`).doc("exam_bad_len").set({
      ...validExamPayload(),
      totalQuestions: 10,
    })
  );
});

test("Owner can create and update submission only when parent exam exists and counts match", async () => {
  const db = aliceDb();
  const subRef = db.collection(`users/${ALICE_UID}/exams/exam_missing/submissions`).doc("sub_1");
  await assertFails(subRef.set(validSubmissionPayload(ALICE_UID, "exam_missing")));

  await assertSucceeds(
    db.collection(`users/${ALICE_UID}/exams`).doc("exam_1").set(validExamPayload())
  );

  const validSubRef = db.collection(`users/${ALICE_UID}/exams/exam_1/submissions`).doc("sub_1");
  await assertSucceeds(validSubRef.set(validSubmissionPayload(ALICE_UID, "exam_1")));

  // Invalid count sum rejected
  const badSubRef = db.collection(`users/${ALICE_UID}/exams/exam_1/submissions`).doc("sub_bad");
  await assertFails(
    badSubRef.set({
      ...validSubmissionPayload(ALICE_UID, "exam_1"),
      correctCount: 5,
      wrongCount: 2,
    })
  );

  // Valid update
  await assertSucceeds(
    validSubRef.update({
      studentName: "Nguyễn Minh Anh (Đã sửa)",
      updatedAt: serverTimestamp(),
    })
  );
});
