/* Decompiler 216ms, total 350ms, lines 78 */
package me.antonschouten.economy.Extern;

import java.awt.EventQueue;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Manager.GuildManager;
import me.antonschouten.economy.Manager.JobManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@SuppressWarnings("serial")
public class AdminPanel extends JFrame {
	
   private DefaultTableModel model;
   private JTable table;
   private List<Player> onlinePlayers;

   public static void main() {
      EventQueue.invokeLater(() -> {
         AdminPanel frame = new AdminPanel();
         frame.setVisible(true);
      });
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
public AdminPanel() {
      this.setTitle("Economy++ - AdminPanel");
      this.setDefaultCloseOperation(2);
      this.setBounds(100, 100, 500, 600);
      this.getContentPane().setLayout((LayoutManager)null);
      JScrollPane scrollPane = new JScrollPane();
      scrollPane.setBounds(48, 41, 390, 500);
      this.getContentPane().add(scrollPane);
      this.model = new DefaultTableModel(new Object[]{"IP", "Name"}, 0);
      this.table = new JTable(this.model);
      scrollPane.setViewportView(this.table);
      this.onlinePlayers = new ArrayList(Bukkit.getOnlinePlayers());
      Iterator var3 = this.onlinePlayers.iterator();

      while(var3.hasNext()) {
         Player player = (Player)var3.next();
         this.model.addRow(new Object[]{player.getAddress().toString().replace("/", ""), player.getName()});
      }

      this.table.setSelectionMode(0);
      this.table.getSelectionModel().addListSelectionListener(new AdminPanel.PlayerInfoListener());
   }

   private void showPlayerInfo(Player player) {
      String info = String.format("<html>\n<body width='300'>\n<center><font color=#008080><h2><b>Player Info</b></h2></font></center>\n<p><font color=#008080><b>Player:</b></font> %s<br>\n<font color=#008080><b>UUID:</b></font> %s<br>\n<font color=#008080><b>Balance:</b></font> $%d<br>\n<font color=#008080><b>Job:</b></font> %s<br>\n<font color=#008080><b>Guild:</b></font> %s</p>\n</body></html>\n", player.getName(), player.getUniqueId(), EconomyManager.getBal(player), JobManager.getPlayerJob(player), GuildManager.getPlayerGuild(player.getName()));
      JOptionPane.showMessageDialog(this, info, "Player Information", 1);
   }

   private class PlayerInfoListener implements ListSelectionListener {
      public void valueChanged(ListSelectionEvent e) {
         if (!e.getValueIsAdjusting()) {
            int selectedRow = AdminPanel.this.table.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < AdminPanel.this.onlinePlayers.size()) {
               Player selectedPlayer = Bukkit.getPlayer((String)AdminPanel.this.model.getValueAt(selectedRow, 1));
               if (selectedPlayer != null) {
                  AdminPanel.this.showPlayerInfo(selectedPlayer);
               } else {
                  JOptionPane.showMessageDialog(AdminPanel.this, "Player not found, or offline.", "Error", 2);
               }
            }
         }

      }
   }
}