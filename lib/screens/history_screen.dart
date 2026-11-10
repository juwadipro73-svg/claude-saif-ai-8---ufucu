import 'package:flutter/material.dart';
import '../models/history_item.dart';

class HistoryScreen extends StatelessWidget {
  final List<HistoryItem> history;
  final VoidCallback onClear;

  const HistoryScreen({Key? key, required this.history, required this.onClear}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    if (history.isEmpty) {
      return const Center(
        child: Text('No calculations yet', style: TextStyle(color: Colors.grey, fontSize: 16)),
      );
    }

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Recent History', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
              TextButton.icon(
                icon: const Icon(Icons.delete_outline, size: 18, color: Colors.redAccent),
                label: const Text('Clear', style: TextStyle(color: Colors.redAccent)),
                onPressed: onClear,
              ),
            ],
          ),
        ),
        Expanded(
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: history.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final item = history[index];
              return ListTile(
                title: Text(item.expression, style: const TextStyle(color: Colors.grey)),
                trailing: Text(
                  item.result,
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.tealAccent),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
