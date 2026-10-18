package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateExistsZeroRows(t *testing.T) {
	rule := loadRule(t, "EliminateExistsZeroRows")
	e := newEnv(t)
	outer, _ := e.scan("u", 2)
	z := e.f.Metadata().AddColumn("z", types.Int)
	zeroRows := e.empty(z)
	e.expectRewrite(rule,
		e.f.ConstructSelect(outer, e.filters(e.exists(zeroRows))),
		e.f.ConstructSelect(outer, e.filters(e.f.ConstructFalse())))

	z2 := e.f.Metadata().AddColumn("z2", types.Int)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(e.empty(z, z2)))))
	oneRow := e.intValues([]opt.ColumnID{z}, []int{1})
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(oneRow))))
	ts, _ := e.scan("t", 1)
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.exists(ts))))
	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(e.f.ConstructNot(e.exists(zeroRows)))))
}
