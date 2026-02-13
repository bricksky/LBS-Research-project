package ICN.itrc_project.lbsproducer.dto;

public record LocationRequest(
        String trj_id,
        String driving_mode,
        String osname,
        Long pingtimestamp,
        Double rawlat,
        Double rawlng,
        Double speed,
        Integer bearing,
        Double accuracy
) {}