package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateMapObjects(t *testing.T) {
	rule := loadRule(t, "EliminateMapObjects")
	e := newEnv(t)
	s, cols := e.scan("t", 1)
	before := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, before, s)

	wide, wc := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructProject(wide, nil, opt.MakeColSet(wc[0])))
	e.expectNoMatch(rule, e.f.ConstructProject(wide, nil, opt.MakeColSet(wc[0], wc[1])))

	nullable := e.f.ConstructScan(&memo.ScanPrivate{
		Table: e.f.Metadata().ColumnMeta(wc[1]).Table,
		Cols:  opt.MakeColSet(wc[1]),
	})
	e.expectNoMatch(rule, e.f.ConstructProject(nullable, nil, opt.MakeColSet(wc[1])))

	computed := e.f.Metadata().AddColumn("n", types.Int)
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{{Element: e.num(1), Col: computed}}, opt.MakeColSet()))
}
