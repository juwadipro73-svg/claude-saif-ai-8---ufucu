import { validJobId } from './policy.mjs';

// The owner's separate Builder repository keeps its workflow at build.yml.
// Override BUILDER_WORKFLOW only if that repository is deliberately reconfigured.
export const DEFAULT_BUILDER_WORKFLOW = 'build.yml';

export function createGithubDispatch({ repo, token, ref = 'main',
  workflow = DEFAULT_BUILDER_WORKFLOW, request = fetch }) {
  if (typeof repo !== 'string' || !/^[A-Za-z0-9][\w.-]*\/[A-Za-z0-9_.-]+$/.test(repo) ||
      ['.', '..'].includes(repo.split('/')[1]))
    throw new Error('BUILDER_REPO must be owner/name');
  if (typeof token !== 'string' || !token)
    throw new Error('Configure GITHUB_DISPATCH_TOKEN in the backend runtime');
  if (typeof workflow !== 'string' || !/^[\w.-]+\.ya?ml$/.test(workflow))
    throw new Error('BUILDER_WORKFLOW must name a workflow YAML file');
  if (typeof ref !== 'string' || !/^[A-Za-z0-9._/-]+$/.test(ref) || ref.includes('..'))
    throw new Error('BUILDER_REF must be a branch or tag name');

  return async jobId => {
    if (!validJobId(jobId)) throw new Error('Invalid job ID');
    const response = await request(`https://api.github.com/repos/${repo}/actions/workflows/${encodeURIComponent(workflow)}/dispatches`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'saif-ai-cloud-gateway' },
      // Never put a prompt, owner UID, artifact metadata or callback secret in Actions inputs.
      body: JSON.stringify({ ref, inputs: { job_id: jobId } })
    });
    if (response.status !== 204) {
      // Do not expose the GitHub response: it can contain private repository metadata.
      throw new Error(`GitHub dispatch failed with status ${response.status}`);
    }
  };
}
