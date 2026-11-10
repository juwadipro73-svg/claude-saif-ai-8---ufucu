import 'package:flutter/material.dart';

class ConverterScreen extends StatefulWidget {
  const ConverterScreen({Key? key}) : super(key: key);

  @override
  State<ConverterScreen> createState() => _ConverterScreenState();
}

class _ConverterScreenState extends State<ConverterScreen> {
  String _category = 'Length';
  String _fromUnit = 'Meter';
  String _toUnit = 'Kilometer';
  double _fromValue = 1.0;
  String _toValue = '';

  final Map<String, Map<String, double>> _conversionRates = {
    'Length': {
      'Meter': 1.0,
      'Kilometer': 1000.0,
      'Centimeter': 0.01,
      'Millimeter': 0.001,
      'Mile': 1609.34,
      'Yard': 0.9144,
      'Foot': 0.3048,
      'Inch': 0.0254,
    },
    'Weight': {
      'Kilogram': 1.0,
      'Gram': 0.001,
      'Milligram': 0.000001,
      'Pound': 0.453592,
      'Ounce': 0.0283495,
      'Metric Ton': 1000.0,
    },
    'Speed': {
      'm/s': 1.0,
      'km/h': 0.277778,
      'mph': 0.44704,
      'knot': 0.514444,
    },
    'Data Storage': {
      'Byte': 1.0,
      'Kilobyte': 1024.0,
      'Megabyte': 1048576.0,
      'Gigabyte': 1073741824.0,
      'Terabyte': 1099511627776.0,
    },
    'Temperature': {
      'Celsius': 0.0,
      'Fahrenheit': 0.0,
      'Kelvin': 0.0,
    }
  };

  @override
  void initState() {
    super.initState();
    _calculate();
  }

  void _calculate() {
    if (_category == 'Temperature') {
      double res = 0;
      if (_fromUnit == _toUnit) {
        res = _fromValue;
      } else if (_fromUnit == 'Celsius' && _toUnit == 'Fahrenheit') {
        res = (_fromValue * 9 / 5) + 32;
      } else if (_fromUnit == 'Celsius' && _toUnit == 'Kelvin') {
        res = _fromValue + 273.15;
      } else if (_fromUnit == 'Fahrenheit' && _toUnit == 'Celsius') {
        res = (_fromValue - 32) * 5 / 9;
      } else if (_fromUnit == 'Fahrenheit' && _toUnit == 'Kelvin') {
        res = ((_fromValue - 32) * 5 / 9) + 273.15;
      } else if (_fromUnit == 'Kelvin' && _toUnit == 'Celsius') {
        res = _fromValue - 273.15;
      } else if (_fromUnit == 'Kelvin' && _toUnit == 'Fahrenheit') {
        res = ((_fromValue - 273.15) * 9 / 5) + 32;
      }
      _toValue = res.toStringAsFixed(3);
    } else {
      final rates = _conversionRates[_category]!;
      double base = _fromValue * (rates[_fromUnit] ?? 1.0);
      double res = base / (rates[_toUnit] ?? 1.0);
      _toValue = res.toStringAsPrecision(7).replaceAll(RegExp(r'0+$'), '').replaceAll(RegExp(r'\.$'), '');
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final categories = _conversionRates.keys.toList();
    final units = _conversionRates[_category]!.keys.toList();

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          DropdownButtonFormField<String>(
            value: _category,
            decoration: const InputDecoration(
              labelText: 'Conversion Category',
              border: OutlineInputBorder(),
            ),
            items: categories.map((c) => DropdownMenuItem(value: c, child: Text(c))).toList(),
            onChanged: (cat) {
              if (cat != null) {
                setState(() {
                  _category = cat;
                  final newUnits = _conversionRates[cat]!.keys.toList();
                  _fromUnit = newUnits.first;
                  _toUnit = newUnits.length > 1 ? newUnits[1] : newUnits.first;
                  _calculate();
                });
              }
            },
          ),
          const SizedBox(height: 20),

          // From Field
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('From', style: TextStyle(color: Colors.grey)),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          initialValue: _fromValue.toString(),
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          decoration: const InputDecoration(border: OutlineInputBorder()),
                          onChanged: (val) {
                            _fromValue = double.tryParse(val) ?? 0.0;
                            _calculate();
                          },
                        ),
                      ),
                      const SizedBox(width: 10),
                      DropdownButton<String>(
                        value: _fromUnit,
                        items: units.map((u) => DropdownMenuItem(value: u, child: Text(u))).toList(),
                        onChanged: (u) {
                          if (u != null) {
                            setState(() {
                              _fromUnit = u;
                              _calculate();
                            });
                          }
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          Center(
            child: IconButton(
              icon: const Icon(Icons.swap_vert, size: 30, color: Colors.blueAccent),
              onPressed: () {
                setState(() {
                  final tmp = _fromUnit;
                  _fromUnit = _toUnit;
                  _toUnit = tmp;
                  _calculate();
                });
              },
            ),
          ),

          // To Field
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('To', style: TextStyle(color: Colors.grey)),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          _toValue,
                          style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                        ),
                      ),
                      DropdownButton<String>(
                        value: _toUnit,
                        items: units.map((u) => DropdownMenuItem(value: u, child: Text(u))).toList(),
                        onChanged: (u) {
                          if (u != null) {
                            setState(() {
                              _toUnit = u;
                              _calculate();
                            });
                          }
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
