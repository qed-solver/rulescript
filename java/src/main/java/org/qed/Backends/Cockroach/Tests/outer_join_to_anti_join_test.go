package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
)

func TestOuterJoinToAntiJoin(t *testing.T) {
	rule := loadRule(t, "OuterJoinToAntiJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("nn", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	isNull := e.f.ConstructIs(e.v(rc[0]), memo.NullSingleton)
	outer := e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	before := e.f.ConstructProject(e.f.ConstructSelect(outer, e.filters(isNull)), nil, opt.MakeColSet(lc[0]))
	want := e.f.ConstructAntiJoin(l, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(outer, e.filters(isNull)))
	isNotNull := e.f.ConstructIsNot(e.v(rc[0]), memo.NullSingleton)
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructSelect(outer, e.filters(isNotNull)), nil, opt.MakeColSet(lc[0])))
	inner := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructSelect(inner, e.filters(isNull)), nil, opt.MakeColSet(lc[0])))

	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, "t")
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	nc := tabID.ColumnID(1)
	nullable := e.f.ConstructScan(&memo.ScanPrivate{Table: tabID, Cols: opt.MakeColSet(nc)})
	nullableOuter := e.f.ConstructLeftJoin(l, nullable, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(nc))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructSelect(nullableOuter, e.filters(e.f.ConstructIs(e.v(nc), memo.NullSingleton))), nil, opt.MakeColSet(lc[0])))
}
