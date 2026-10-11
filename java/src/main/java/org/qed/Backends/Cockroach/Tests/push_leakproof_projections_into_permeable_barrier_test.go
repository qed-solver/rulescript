package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushLeakproofProjectionsIntoPermeableBarrier(t *testing.T) {
	rule := loadRule(t, "PushLeakproofProjectionsIntoPermeableBarrier")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	computed := e.f.Metadata().AddColumn("computed", types.Int)
	item := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), computed),
		}
	}
	barrier := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	before := e.f.ConstructProject(barrier, item(), opt.MakeColSet(cols[0]))
	below := e.f.ConstructProject(s, item(), opt.MakeColSet(cols[0]))
	want := e.f.ConstructProject(below, nil, opt.MakeColSet(cols[0], computed))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(s, item(), opt.MakeColSet(cols[0])))
	e.expectNoMatch(rule, e.f.ConstructProject(barrier, item(), opt.ColSet{}))
	e.expectNoMatch(rule, barrier)
}

func TestSQLPushLeakproofProjectionsIntoPermeableBarrier(t *testing.T) {
	expectSQLFiresWith(t, "PushLeakproofProjectionsIntoPermeableBarrier",
		"SELECT x + 1, x FROM (SELECT x FROM u)",
		sqlHelpers{names: []string{"PruneScanCols"}})
}
