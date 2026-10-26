document.addEventListener('DOMContentLoaded', () => {
    const historyDisplay = document.getElementById('history-display');
    const mainDisplay = document.getElementById('main-display');
    const fractionDisplay = document.getElementById('fraction-display');
    const keys = document.querySelectorAll('.calc-btn');

    let currentExpression = '0';
    let previousExpression = '';
    let lastEvaluatedResult = null;
    let lastEvaluatedFraction = null;
    let isShowingFraction = false;
    let isCalculated = false;

    function updateDisplay() {
        if (isCalculated && isShowingFraction && lastEvaluatedFraction) {
            mainDisplay.textContent = lastEvaluatedFraction;
            fractionDisplay.textContent = `Decimal: ${lastEvaluatedResult}`;
            fractionDisplay.classList.add('visible');
        } else {
            mainDisplay.textContent = currentExpression;
            if (lastEvaluatedFraction && isCalculated && lastEvaluatedFraction !== lastEvaluatedResult) {
                fractionDisplay.textContent = `Fraction: ${lastEvaluatedFraction}`;
                fractionDisplay.classList.add('visible');
            } else {
                fractionDisplay.classList.remove('visible');
            }
        }
        mainDisplay.classList.remove('error');
        historyDisplay.textContent = previousExpression;
    }

    function appendCharacter(char) {
        if (isCalculated) {
            if (['+', '-', '×', '÷', '%'].includes(char)) {
                currentExpression = lastEvaluatedResult !== null ? String(lastEvaluatedResult) : '0';
            } else {
                currentExpression = '';
            }
            isCalculated = false;
            isShowingFraction = false;
            lastEvaluatedFraction = null;
        }

        if (currentExpression === '0' && char !== '.' && !['+', '-', '×', '÷', '%', ')'].includes(char)) {
            currentExpression = char;
        } else {
            const lastChar = currentExpression.slice(-1);
            const operators = ['+', '-', '×', '÷'];
            if (operators.includes(lastChar) && operators.includes(char)) {
                currentExpression = currentExpression.slice(0, -1) + char;
            } else {
                currentExpression += char;
            }
        }
        updateDisplay();
    }

    function clearAll() {
        currentExpression = '0';
        previousExpression = '';
        lastEvaluatedResult = null;
        lastEvaluatedFraction = null;
        isShowingFraction = false;
        isCalculated = false;
        fractionDisplay.classList.remove('visible');
        updateDisplay();
    }

    function deleteLast() {
        if (isCalculated) {
            clearAll();
            return;
        }
        if (currentExpression.length > 1) {
            currentExpression = currentExpression.slice(0, -1);
        } else {
            currentExpression = '0';
        }
        updateDisplay();
    }

    function toggleFraction() {
        if (!isCalculated || !lastEvaluatedFraction) {
            return;
        }
        isShowingFraction = !isShowingFraction;
        updateDisplay();
    }

    async function evaluate() {
        if (isCalculated) return;
        const exprToEval = currentExpression;

        try {
            const response = await fetch('/api/calculate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ expression: exprToEval })
            });

            if (response.ok) {
                const data = await response.json();
                if (data.error) {
                    showError(data.error);
                } else {
                    previousExpression = `${exprToEval} =`;
                    currentExpression = data.result;
                    lastEvaluatedResult = data.result;
                    lastEvaluatedFraction = data.fraction;
                    isCalculated = true;
                    isShowingFraction = false;
                    updateDisplay();
                }
            } else {
                fallbackEvaluate(exprToEval);
            }
        } catch (err) {
            fallbackEvaluate(exprToEval);
        }
    }

    function fallbackEvaluate(expr) {
        try {
            const sanitized = expr
                .replace(/×/g, '*')
                .replace(/÷/g, '/')
                .replace(/−/g, '-')
                .replace(/(\d+(\.\d+)?)%/g, '($1/100)');

            if (!/^[\d\.\+\-\*\/\(\)\s]+$/.test(sanitized)) {
                showError('Syntax Error');
                return;
            }

            const calcFunction = new Function(`return (${sanitized});`);
            const rawResult = calcFunction();

            if (!isFinite(rawResult)) {
                showError('Cannot divide by zero');
                return;
            }

            const rounded = Math.round(rawResult * 1e10) / 1e10;
            const resStr = String(rounded);
            previousExpression = `${expr} =`;
            currentExpression = resStr;
            lastEvaluatedResult = resStr;
            lastEvaluatedFraction = computeFraction(rounded);
            isCalculated = true;
            isShowingFraction = false;
            updateDisplay();
        } catch (e) {
            showError('Syntax Error');
        }
    }

    function computeFraction(val) {
        if (!isFinite(val)) return null;
        const sign = val < 0 ? -1 : 1;
        const abs = Math.abs(val);
        const whole = Math.floor(abs);
        const frac = abs - whole;

        if (frac < 1e-9) return String(sign * whole);

        let h1 = 1, h2 = 0, k1 = 0, k2 = 1;
        let b = frac;
        do {
            let a = Math.floor(b);
            let aux = h1;
            h1 = a * h1 + h2;
            h2 = aux;
            aux = k1;
            k1 = a * k1 + k2;
            k2 = aux;
            b = 1.0 / (b - a);
        } while (Math.abs(frac - h1 / k1) > frac * 1e-7 && k1 < 100000);

        const totalNum = (whole * k1 + h1) * sign;
        return `${totalNum}/${k1}`;
    }

    function showError(message) {
        previousExpression = currentExpression;
        currentExpression = message;
        mainDisplay.textContent = message;
        mainDisplay.classList.add('error');
        fractionDisplay.classList.remove('visible');
        isCalculated = true;
        lastEvaluatedResult = null;
        lastEvaluatedFraction = null;
    }

    keys.forEach(btn => {
        btn.addEventListener('click', () => {
            const action = btn.dataset.action;
            const value = btn.dataset.value;

            if (action === 'clear') {
                clearAll();
            } else if (action === 'delete') {
                deleteLast();
            } else if (action === 'fraction') {
                toggleFraction();
            } else if (action === 'calculate') {
                evaluate();
            } else if (value) {
                appendCharacter(value);
            }
        });
    });

    window.addEventListener('keydown', (e) => {
        if (e.key >= '0' && e.key <= '9') {
            appendCharacter(e.key);
        } else if (['+', '-', '(', ')', '%', '.'].includes(e.key)) {
            appendCharacter(e.key);
        } else if (e.key === '*') {
            appendCharacter('×');
        } else if (e.key === '/') {
            e.preventDefault();
            appendCharacter('÷');
        } else if (e.key === 'Enter' || e.key === '=') {
            e.preventDefault();
            evaluate();
        } else if (e.key === 'Backspace') {
            deleteLast();
        } else if (e.key === 'Escape' || e.key.toLowerCase() === 'c') {
            clearAll();
        } else if (e.key.toLowerCase() === 'f') {
            toggleFraction();
        }
    });

    updateDisplay();
});
