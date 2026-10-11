package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveZeroCardLock(t *testing.T) {
	rule := loadRule(t, "RemoveZeroCardLock")
	e := newEnv(t)
	c := e.f.Metadata().AddColumn("c", types.Int)
	input := e.empty(c)
	before := e.f.ConstructSelect(input, e.filters(e.f.ConstructGt(e.v(c), e.num(0))))
	e.expectRewrite(rule, before, input)

	e.expectNoMatch(rule, input)
	scan, sc := e.scan("t", 1)
	e.expectNoMatch(rule, e.f.ConstructSelect(scan, e.filters(e.f.ConstructGt(e.v(sc[0]), e.num(0)))))
	c2 := e.f.Metadata().AddColumn("c2", types.Int)
	e.expectNoMatch(rule, e.f.ConstructSelect(e.empty(c, c2), e.filters(e.f.ConstructGt(e.v(c), e.num(0)))))
}

func TestSQLRemoveZeroCardLock(t *testing.T) {
	expectSQLFiresWith(t, "RemoveZeroCardLock",
		"SELECT * FROM (SELECT x FROM u WHERE false) WHERE x > 0",
		sqlHelpers{limits: map[string]int{"SimplifyZeroCardinalityGroup": 1}, names: []string{"EliminateProject", "PruneValuesCols", "PruneScanCols"}})
}
