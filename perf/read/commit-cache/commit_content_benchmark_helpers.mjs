export function utf8ByteLength(value) {
  let bytes = 0;
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 0x7f) {
      bytes += 1;
    } else if (code <= 0x7ff) {
      bytes += 2;
    } else if (code >= 0xd800 && code <= 0xdbff && index + 1 < value.length) {
      const next = value.charCodeAt(index + 1);
      if (next >= 0xdc00 && next <= 0xdfff) {
        bytes += 4;
        index += 1;
      } else {
        bytes += 3;
      }
    } else {
      bytes += 3;
    }
  }
  return bytes;
}

export function validBlock(content, index) {
  const block = Array.isArray(content) && Number.isInteger(index) ? content[index] : undefined;
  return Boolean(block && block.id !== undefined && block.id !== null && block.data && typeof block.data.text === 'string');
}

export function summaryLoad(scenario) {
  if (scenario.executor === 'ramping-arrival-rate') {
    return { rate: scenario.stages[scenario.stages.length - 1].target };
  }
  return { vus: scenario.vus };
}

function withoutAuthFields(value) {
  if (Array.isArray(value)) return value.map(withoutAuthFields);
  if (!value || typeof value !== 'object') return value;

  return Object.fromEntries(
    Object.entries(value)
      .filter(([key]) => !/(cookie|session)/i.test(key))
      .map(([key, nested]) => [key, withoutAuthFields(nested)]),
  );
}

export function summaryDataWithoutAuth(data) {
  if (!data || typeof data !== 'object' || !Object.hasOwn(data, 'setup_data')) return data;
  return { ...data, setup_data: withoutAuthFields(data.setup_data) };
}
