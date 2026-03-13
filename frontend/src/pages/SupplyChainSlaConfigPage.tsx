import { useEffect, useState } from 'react';
import { getSlaRules, updateSlaRule } from '../services/api';
import type { SlaRule } from '../types/supplyChain';
import { formatDateTimeCompact } from '../utils/formatters';

type SlaDrafts = Record<number, { targetDays: string; warningDays: string }>;

function toDrafts(rules: SlaRule[]): SlaDrafts {
  return rules.reduce(
    (drafts, rule) => ({
      ...drafts,
      [rule.id]: {
        targetDays: String(rule.targetDays),
        warningDays: String(rule.warningDays),
      },
    }),
    {} as SlaDrafts,
  );
}

export function SupplyChainSlaConfigPage() {
  const [rules, setRules] = useState<SlaRule[]>([]);
  const [drafts, setDrafts] = useState<SlaDrafts>({});
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadRules() {
      setLoading(true);
      setErrorMessage(null);

      try {
        const response = await getSlaRules();
        if (!cancelled) {
          setRules(response);
          setDrafts(toDrafts(response));
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : 'Unable to load SLA rules.');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadRules();

    return () => {
      cancelled = true;
    };
  }, []);

  function handleDraftChange(id: number, field: 'targetDays' | 'warningDays', value: string) {
    setDrafts((current) => ({
      ...current,
      [id]: {
        ...current[id],
        [field]: value,
      },
    }));
  }

  async function handleSave(rule: SlaRule) {
    const draft = drafts[rule.id];
    if (!draft) {
      return;
    }

    setSavingId(rule.id);
    setErrorMessage(null);

    try {
      const response = await updateSlaRule(rule.id, {
        targetDays: Number(draft.targetDays),
        warningDays: Number(draft.warningDays),
        active: rule.active,
      });

      setRules((current) => current.map((item) => (item.id === response.id ? response : item)));
      setDrafts((current) => ({
        ...current,
        [response.id]: {
          targetDays: String(response.targetDays),
          warningDays: String(response.warningDays),
        },
      }));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to save SLA rule.');
    } finally {
      setSavingId(null);
    }
  }

  return (
    <section className="module-stack">
      {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}

      <section className="panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">SLA Configuration</p>
            <h2>Standard transition rules</h2>
            <p className="hero-copy compact-copy">
              Adjust target days and warning windows for each step in the standard logistics flow.
            </p>
          </div>
        </div>

        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Transition</th>
                <th>Target Days</th>
                <th>Warning Days</th>
                <th>Updated</th>
                <th>Save</th>
              </tr>
            </thead>
            <tbody>
              {rules.length > 0 ? (
                rules.map((rule) => (
                  <tr key={rule.id}>
                    <td>
                      <div className="invoice-primary-cell">
                        <strong>{rule.startMilestoneLabel}</strong>
                        <span>{rule.endMilestoneLabel}</span>
                      </div>
                    </td>
                    <td>
                      <input
                        type="number"
                        min="1"
                        step="1"
                        value={drafts[rule.id]?.targetDays ?? ''}
                        onChange={(event) => handleDraftChange(rule.id, 'targetDays', event.target.value)}
                      />
                    </td>
                    <td>
                      <input
                        type="number"
                        min="0"
                        step="1"
                        value={drafts[rule.id]?.warningDays ?? ''}
                        onChange={(event) => handleDraftChange(rule.id, 'warningDays', event.target.value)}
                      />
                    </td>
                    <td>{formatDateTimeCompact(rule.updatedAt)}</td>
                    <td>
                      <button
                        type="button"
                        className="primary-button"
                        disabled={savingId === rule.id}
                        onClick={() => handleSave(rule)}
                      >
                        {savingId === rule.id ? 'Saving...' : 'Save'}
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-state">
                      {loading ? 'Loading SLA rules...' : 'No SLA rules configured.'}
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}
