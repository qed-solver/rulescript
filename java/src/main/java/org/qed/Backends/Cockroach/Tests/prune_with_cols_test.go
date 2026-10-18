package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneWithCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneWithCols")

	input, key := singleColumnScan(e, "t", 0)
	transparent := e.f.ConstructProject(input, nil, opt.MakeColSet(key))
	computed := e.f.Metadata().AddColumn("computed", types.Int)
	before := e.f.ConstructProject(transparent, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(7), computed),
	}, opt.MakeColSet(key))

	pushed := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(7), computed),
	}, opt.MakeColSet(key))
	want := e.f.ConstructProject(pushed, nil, opt.MakeColSet(key, computed))
	e.expectRewrite(rule, before, want)

	onlyComputed := e.f.ConstructProject(transparent, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(7), e.f.Metadata().AddColumn("alone", types.Int)),
	}, opt.ColSet{})
	e.expectNoMatch(rule, onlyComputed)
}
