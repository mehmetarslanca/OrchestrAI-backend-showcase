-- V17: Backfill project artifacts to typed, versioned schema.

WITH normalized_tree AS (
    SELECT
        project_id,
        jsonb_build_object(
            'version', COALESCE(final_tree->>'version', 'v1'),
            'tree', COALESCE((
                SELECT jsonb_agg(
                    jsonb_build_object(
                        'path', node->>'path',
                        'content', COALESCE(node->>'content', ''),
                        'contentEncoding', COALESCE(node->>'contentEncoding', 'utf-8'),
                        'binary', CASE
                            WHEN node ? 'binary' THEN (node->>'binary')::boolean
                            ELSE false
                        END,
                        'sizeBytes', CASE
                            WHEN node ? 'sizeBytes' THEN (node->>'sizeBytes')::bigint
                            ELSE octet_length(COALESCE(node->>'content', ''))
                        END
                    )
                )
                FROM jsonb_array_elements(COALESCE(final_tree->'tree', '[]'::jsonb)) AS node
            ), '[]'::jsonb)
        ) AS typed_final_tree
    FROM project_artifacts
    WHERE final_tree IS NOT NULL
)
UPDATE project_artifacts artifact
SET final_tree = normalized_tree.typed_final_tree
FROM normalized_tree
WHERE artifact.project_id = normalized_tree.project_id;

WITH artifact_summary AS (
    SELECT
        project_id,
        final_tree,
        COALESCE(jsonb_array_length(final_tree->'tree'), 0) AS artifact_file_count,
        COALESCE((
            SELECT SUM(
                CASE
                    WHEN node ? 'sizeBytes' THEN (node->>'sizeBytes')::bigint
                    ELSE octet_length(COALESCE(node->>'content', ''))
                END
            )
            FROM jsonb_array_elements(COALESCE(final_tree->'tree', '[]'::jsonb)) AS node
        ), 0) AS artifact_total_bytes
    FROM project_artifacts
    WHERE final_tree IS NOT NULL
)
UPDATE project_artifacts artifact
SET metadata = COALESCE(artifact.metadata, '{}'::jsonb)
    || jsonb_build_object(
        'artifactSchemaVersion', COALESCE(artifact.metadata->>'artifactSchemaVersion', artifact_summary.final_tree->>'version', 'v1'),
        'artifactFileCount', artifact_summary.artifact_file_count,
        'artifactTotalBytes', artifact_summary.artifact_total_bytes,
        'artifactAvailable', artifact_summary.artifact_file_count > 0,
        'deliveryTarget', COALESCE(artifact.metadata->>'deliveryTarget', artifact.metadata->>'ideTarget'),
        'generationCompletionTimestamp', COALESCE(artifact.metadata->>'generationCompletionTimestamp', artifact.metadata->>'terminalStateAt')
    )
FROM artifact_summary
WHERE artifact.project_id = artifact_summary.project_id;
