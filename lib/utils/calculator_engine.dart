import 'dart:math' as math;
import 'package:math_expressions/math_expressions.dart';

class CalculatorEngine {
  static bool isDegree = true;

  static String evaluate(String raw) {
    if (raw.trim().isEmpty) return '0';
    try {
      String expr = raw
          .replaceAll('×', '*')
          .replaceAll('÷', '/')
          .replaceAll('π', '${math.pi}')
          .replaceAll('e', '${math.e}');

      // Handle trig conversions if in degrees
      if (isDegree) {
        expr = _wrapTrigInDegrees(expr);
      }

      // Handle factorial n!
      expr = _evalFactorials(expr);

      Parser p = Parser();
      Expression exp = p.parse(expr);
      ContextModel cm = ContextModel();
      double eval = exp.evaluate(EvaluationType.REAL, cm);

      if (eval.isNaN) return 'Error';
      if (eval.isInfinite) return eval > 0 ? '∞' : '-∞';

      // Format clean integer or fixed decimal
      if (eval == eval.roundToDouble() && eval.abs() < 1e12) {
        return eval.toInt().toString();
      }
      return eval.toStringAsPrecision(10).replaceAll(RegExp(r'0+$'), '').replaceAll(RegExp(r'\.$'), '');
    } catch (e) {
      return 'Error';
    }
  }

  static String _wrapTrigInDegrees(String expr) {
    // Replace sin(x) with sin(x * pi / 180)
    return expr
        .replaceAllMapped(RegExp(r'sin\(([^)]+)\)'), (m) => 'sin((${m[1]}) * (${math.pi} / 180))')
        .replaceAllMapped(RegExp(r'cos\(([^)]+)\)'), (m) => 'cos((${m[1]}) * (${math.pi} / 180))')
        .replaceAllMapped(RegExp(r'tan\(([^)]+)\)'), (m) => 'tan((${m[1]}) * (${math.pi} / 180))');
  }

  static String _evalFactorials(String expr) {
    return expr.replaceAllMapped(RegExp(r'(\d+)!'), (m) {
      int val = int.tryParse(m[1]!) ?? 0;
      if (val > 100) return '9999999999';
      double res = 1;
      for (int i = 2; i <= val; i++) res *= i;
      return res.toString();
    });
  }
}
