import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getAppCheck } from 'firebase-admin/app-check';
import { getDatabase } from 'firebase-admin/database';
import { Storage } from '@google-cloud/storage';
import { google } from 'googleapis';
import { createApp } from './server.mjs';
import { createGithubDispatch, DEFAULT_BUILDER_WORKFLOW } from './dispatch.mjs';

function required(key) {
  const value = process.env[key];
  if (!value) throw new Error(`Configure ${key} in the backend runtime`);
  return value;
}
const databaseURL = required('FIREBASE_DATABASE_URL');
const projectId = required('FIREBASE_PROJECT_ID');
const bucketName = required('STAGING_BUCKET');
const repo = required('BUILDER_REPO');
const githubToken = required('GITHUB_DISPATCH_TOKEN');
const githubRef = process.env.BUILDER_REF || 'main';
const workflow = process.env.BUILDER_WORKFLOW || DEFAULT_BUILDER_WORKFLOW;
const config = {
  appCheckAppId: required('FIREBASE_ANDROID_APP_ID'),
  builderRef: githubRef,
  driveFolderId: required('DRIVE_FOLDER_ID'),
  downloadSecret: required('DOWNLOAD_SECRET'),
  workerSecret: required('WORKER_CALLBACK_SECRET')
};
if (config.downloadSecret.length < 32 || config.workerSecret.length < 32)
  throw new Error('DOWNLOAD_SECRET and WORKER_CALLBACK_SECRET must each have 32+ random characters');

const firebase = initializeApp({ credential: applicationDefault(), projectId, databaseURL });
const db = getDatabase(firebase);
const bucket = new Storage().bucket(bucketName);
let driveAuth;
if (process.env.DRIVE_REFRESH_TOKEN) {
  const oauth = new google.auth.OAuth2(required('DRIVE_CLIENT_ID'), required('DRIVE_CLIENT_SECRET'));
  oauth.setCredentials({ refresh_token: required('DRIVE_REFRESH_TOKEN') });
  driveAuth = oauth;
} else {
  // Read builder output and create private user-upload files in the configured Drive folder.
  driveAuth = new google.auth.GoogleAuth({ scopes: [
    'https://www.googleapis.com/auth/drive.readonly', 'https://www.googleapis.com/auth/drive.file'
  ] });
}
const drive = google.drive({ version: 'v3', auth: driveAuth });

const dispatch = createGithubDispatch({ repo, token: githubToken, ref: githubRef, workflow });

const app = createApp({ db, auth: getAuth(firebase), appCheck: getAppCheck(firebase), bucket,
  drive, config, dispatch });
const port = Number(process.env.PORT || 8080);
app.listen(port, '0.0.0.0', () => console.log(`SAIF AI Cloud gateway listening on ${port}`));
