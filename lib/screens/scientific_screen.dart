import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../utils/calculator_engine.dart';
import '../models/history_item.dart';

class ScientificScreen extends StatefulWidget {
  final Function(HistoryItem) onNewHistory;

  const ScientificScreen({Key? key, required this.onNewHistory}) : super(key: key);

  @override
  State<ScientificScreen> createState() => _ScientificScreenState();
}

class _ScientificScreenState extends State<ScientificScreen> {
  String _expression = '';
  String _result = '0';
  bool _isDeg = true;
  bool _isSec = false;
  double _memory = 0.0;
  bool _hasMemory = false;

  void _onKey(String val) {
    HapticFeedback.lightImpact();
    setState(() {
      _expression += val;
      _result = CalculatorEngine.evaluate(_expression);
    });
  }

  void _onOp(String op) {
    HapticFeedback.lightImpact();
    setState(() {
      _expression += ' $op ';
    });
  }

  void _onFn(String fn) {
    HapticFeedback.lightImpact();
    setState(() {
      if (fn == 'sin') _expression += _isSec ? 'arcsin(' : 'sin(';
      else if (fn == 'cos') _expression += _isSec ? 'arccos(' : 'cos(';
      else if (fn == 'tan') _expression += _isSec ? 'arctan(' : 'tan(';
      else if (fn == 'ln') _expression += 'ln(';
      else if (fn == 'log') _expression += 'log(';
      else if (fn == 'sqrt') _expression += 'sqrt(';
      else if (fn == 'sq') _expression += '^2';
      else if (fn == 'pow') _expression += '^';
      else if (fn == 'fact') _expression += '!';
      else if (fn == 'inv') _expression += '1/(';
      _result = CalculatorEngine.evaluate(_expression);
    });
  }

  void _clear() {
    HapticFeedback.mediumImpact();
    setState(() {
      _expression = '';
      _result = '0';
    });
  }

  void _backspace() {
    HapticFeedback.lightImpact();
    setState(() {
      if (_expression.isNotEmpty) {
        _expression = _expression.trimRight();
        _expression = _expression.substring(0, _expression.length - 1).trimRight();
        _result = _expression.isNotEmpty ? CalculatorEngine.evaluate(_expression) : '0';
      }
    });
  }

  void _calculate() {
    HapticFeedback.heavyImpact();
    if (_expression.trim().isEmpty) return;
    final finalResult = CalculatorEngine.evaluate(_expression);
    if (finalResult != 'Error') {
      widget.onNewHistory(HistoryItem(
        expression: _expression,
        result: finalResult,
        timestamp: DateTime.now(),
      ));
    }
    setState(() {
      _result = finalResult;
      _expression = finalResult != 'Error' ? finalResult : _expression;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Display Area
        Expanded(
          flex: 3,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            margin: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Theme.of(context).cardColor,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    InkWell(
                      onTap: () {
                        setState(() {
                          _isDeg = !_isDeg;
                          CalculatorEngine.isDegree = _isDeg;
                          _result = CalculatorEngine.evaluate(_expression);
                        });
                      },
                      child: Chip(
                        label: Text(_isDeg ? 'DEG' : 'RAD', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 11)),
                        backgroundColor: Colors.blueGrey.shade800,
                      ),
                    ),
                    if (_hasMemory)
                      const Chip(label: Text('M', style: TextStyle(color: Colors.orange, fontSize: 11)), backgroundColor: Colors.black45),
                    IconButton(
                      icon: const Icon(Icons.copy, size: 18),
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: _result));
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text('Copied: $_result'), duration: const Duration(seconds: 1)),
                        );
                      },
                    ),
                  ],
                ),
                const Spacer(),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  reverse: true,
                  child: Text(
                    _expression,
                    style: const TextStyle(fontSize: 18, color: Colors.grey),
                  ),
                ),
                const SizedBox(height: 8),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  reverse: true,
                  child: Text(
                    _result,
                    style: const TextStyle(fontSize: 38, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
          ),
        ),

        // Memory & Quick bar
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _quickButton('2nd', isToggled: _isSec, onTap: () => setState(() => _isSec = !_isSec)),
              _quickButton('MC', onTap: () => setState(() { _memory = 0; _hasMemory = false; })),
              _quickButton('MR', onTap: () {
                if (_hasMemory) _onKey(_memory.toString());
              }),
              _quickButton('M+', onTap: () {
                _memory += double.tryParse(_result) ?? 0;
                setState(() => _hasMemory = true);
              }),
              _quickButton('M-', onTap: () {
                _memory -= double.tryParse(_result) ?? 0;
                setState(() => _hasMemory = true);
              }),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Keypad Grid
        Expanded(
          flex: 6,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            child: GridView.count(
              crossAxisCount: 5,
              mainAxisSpacing: 6,
              crossAxisSpacing: 6,
              childAspectRatio: 1.15,
              children: [
                _btn('sin', isFn: true, onTap: () => _onFn('sin')),
                _btn('cos', isFn: true, onTap: () => _onFn('cos')),
                _btn('tan', isFn: true, onTap: () => _onFn('tan')),
                _btn('AC', isOp: true, color: Colors.redAccent, onTap: _clear),
                _btn('⌫', isOp: true, onTap: _backspace),

                _btn('ln', isFn: true, onTap: () => _onFn('ln')),
                _btn('log', isFn: true, onTap: () => _onFn('log')),
                _btn('√', isFn: true, onTap: () => _onFn('sqrt')),
                _btn('(', isFn: true, onTap: () => _onKey('(')),
                _btn(')', isFn: true, onTap: () => _onKey(')')),

                _btn('xʸ', isFn: true, onTap: () => _onFn('pow')),
                _btn('7', onTap: () => _onKey('7')),
                _btn('8', onTap: () => _onKey('8')),
                _btn('9', onTap: () => _onKey('9')),
                _btn('÷', isOp: true, color: Colors.orange, onTap: () => _onOp('÷')),

                _btn('x²', isFn: true, onTap: () => _onFn('sq')),
                _btn('4', onTap: () => _onKey('4')),
                _btn('5', onTap: () => _onKey('5')),
                _btn('6', onTap: () => _onKey('6')),
                _btn('×', isOp: true, color: Colors.orange, onTap: () => _onOp('×')),

                _btn('π', isFn: true, onTap: () => _onKey('π')),
                _btn('1', onTap: () => _onKey('1')),
                _btn('2', onTap: () => _onKey('2')),
                _btn('3', onTap: () => _onKey('3')),
                _btn('-', isOp: true, color: Colors.orange, onTap: () => _onOp('-')),

                _btn('e', isFn: true, onTap: () => _onKey('e')),
                _btn('n!', isFn: true, onTap: () => _onFn('fact')),
                _btn('0', onTap: () => _onKey('0')),
                _btn('.', onTap: () => _onKey('.')),
                _btn('+', isOp: true, color: Colors.orange, onTap: () => _onOp('+')),

                _btn('1/x', isFn: true, onTap: () => _onFn('inv')),
                _btn('%', isFn: true, onTap: () => _onKey('%')),
                const SizedBox.shrink(),
                _btn('=', isOp: true, color: Colors.teal, flex: 2, onTap: _calculate),
                const SizedBox.shrink(),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _quickButton(String text, {bool isToggled = false, required VoidCallback onTap}) {
    return TextButton(
      style: TextButton.styleFrom(
        foregroundColor: isToggled ? Colors.amber : Colors.blueGrey,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      ),
      onPressed: onTap,
      child: Text(text, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
    );
  }

  Widget _btn(String label, {bool isFn = false, bool isOp = false, Color? color, int flex = 1, required VoidCallback onTap}) {
    final theme = Theme.of(context);
    Color bgColor = color ?? (isOp ? Colors.orange.shade700 : (isFn ? Colors.blueGrey.shade800 : theme.cardColor));

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        decoration: BoxDecoration(
          color: bgColor,
          borderRadius: BorderRadius.circular(12),
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: TextStyle(
            fontSize: isOp ? 22 : (isFn ? 14 : 18),
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
      ),
    );
  }
}
