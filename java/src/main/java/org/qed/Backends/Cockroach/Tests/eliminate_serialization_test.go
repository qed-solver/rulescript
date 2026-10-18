package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateSerialization(t *testing.T) {
	rule := loadRule(t, "EliminateSerialization")
	e := newEnv(t)
	row, rc := e.scan("t", 1)
	deser := e.f.ConstructEq(e.v(rc[0]), e.num(1))
	out := e.f.Metadata().AddColumn("deser", types.Bool)
	before := e.f.ConstructProject(
		e.f.ConstructSelect(row, e.filters(e.f.ConstructNot(deser))),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(deser, out)},
		opt.ColSet{},
	)
	projOut := e.f.Metadata().AddColumn("deser", types.Bool)
	projected := e.f.ConstructProject(
		row,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(deser, projOut)},
		opt.ColSet{},
	)
	want := e.f.ConstructSelect(projected, e.filters(e.f.ConstructNot(e.v(projOut))))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	otherDeser := e.f.ConstructEq(e.v(rc[0]), e.num(2))
	otherOut := e.f.Metadata().AddColumn("deser", types.Bool)
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructSelect(row, e.filters(e.f.ConstructNot(otherDeser))),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(deser, otherOut)},
		opt.ColSet{},
	))

	passOut := e.f.Metadata().AddColumn("deser", types.Bool)
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructSelect(row, e.filters(e.f.ConstructNot(deser))),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(deser, passOut)},
		opt.MakeColSet(rc[0]),
	))
}
