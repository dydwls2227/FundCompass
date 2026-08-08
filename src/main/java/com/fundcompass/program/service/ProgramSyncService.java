package com.fundcompass.program.service;

import com.fundcompass.program.domain.Program;
import com.fundcompass.program.infra.BizinfoClient;
import com.fundcompass.program.infra.dto.BizinfoProgramItem;
import com.fundcompass.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramSyncService {

    private final BizinfoClient bizinfoClient;
    private final BizinfoProgramMapper mapper;
    private final ProgramRepository programRepository;

    public record SyncResult(int inserted, int updated, int skipped, int failed){
        int total(){
            return inserted + updated + skipped + failed;
        }
    }

    public SyncResult sync(int count){
        List<BizinfoProgramItem> items = bizinfoClient.fetch(count);

        int inserted = 0, updated = 0, skipped = 0, failed = 0;

        for(BizinfoProgramItem item : items){
            try{
                Program incoming = mapper.toEntity(item);
                Optional<Program> found = programRepository.findByPblancId(incoming.getPblancId());

                if(found.isEmpty()){
                    programRepository.save(incoming);
                    inserted++;
                }else{
                    Program existing = found.get();
                    if(existing.needsUpdateFrom(incoming)){
                        existing.updateFrom(incoming);
                        programRepository.save(existing);
                        updated++;
                    }else{
                        skipped++;
                    }
                }
            } catch(Exception e){
                failed++;
                log.error("공고 처리 실패 (pblancId={}) : {}", item.pblancId(),e.getMessage());
            }
        }

        SyncResult result = new SyncResult(inserted, updated, skipped, failed);
        log.info("공고 동기화 완료 - 전체 {}건 (신규 {}, 갱신{}, 변경없음 {}, 실패 {})", result.total(),inserted, updated, skipped, failed);
        return result;
    }
}
