#cloud-config

write_files:
  - path: /etc/netplan/51-cloud-init-subport.yaml.tpl
    owner: root:root
    permissions: '0600'
    content: |
      network:
        version: 2
        vlans:
          __IFACE__.${segmentId}:
            link: "__IFACE__"
            dhcp4: false
            macaddress: "${macaddress}"
            id: ${segmentId}
            addresses:
              - "${ipv4}"
      <#if hostRoutes?has_content>
            routes:
          <#list hostRoutes as key, value>
              - {to: ${key}, via: ${value}}
          </#list>
      </#if>
  - path: /etc/ceph/ceph.client.manila.secret
    permissions: '0600'
    content: |
      ${share_access_key}
  - path: /etc/systemd/system/jupyterlab.service
    permissions: '0644'
    content: |
      [Unit]
      Description=JupyterLab Service
      After=network.target

      [Service]
      Type=simple
      User=ubuntu
      WorkingDirectory=/home/ubuntu
      ExecStart=/home/ubuntu/work/.venv/bin/jupyter-lab --ip=0.0.0.0 --no-browser --IdentityProvider.token='${jupyter_token}' --ServerApp.root_dir=/home/ubuntu/work
      Restart=always
      RestartSec=10

      [Install]
      WantedBy=multi-user.target

runcmd:
  - [sh, -c, 'printf "%s:%s\n" "$1" "$2" | chpasswd', cloud-init-password, "${username?json_string}", "${password?json_string}"]
  - |
    iface=$(ip route | awk '/default/ {print $5}')
    sed "s/__IFACE__/${r"${iface}"}/g" /etc/netplan/51-cloud-init-subport.yaml.tpl > /etc/netplan/51-cloud-init-subport.yaml
  - chmod 600 /etc/netplan/51-cloud-init-subport.yaml
  - ['/usr/sbin/netplan', 'apply']
  - /usr/local/bin/change_mirrors.sh
  - mkdir -p /home/ubuntu/work/share_data
  - echo "${share_export_location} /home/ubuntu/work/share_data ceph name=${share_access_to},secretfile=/etc/ceph/ceph.client.manila.secret,fs=${manila_fs_name},noatime,_netdev 0 0" | tee -a /etc/fstab
  - mount -a
  - chown -R ubuntu:ubuntu /home/ubuntu/work
  - systemctl daemon-reload
  - systemctl enable jupyterlab
  - systemctl start jupyterlab
  - systemctl stop unattended-upgrades && systemctl disable unattended-upgrades
